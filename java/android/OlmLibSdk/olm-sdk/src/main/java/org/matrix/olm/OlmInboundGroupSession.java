/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2016 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.olm;


import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Class used to create an inbound <a href="http://matrix.org/docs/guides/e2e_implementation.html#handling-an-m-room-key-event">Megolm session</a>.<br>
 * Counter part of the outbound group session {@link OlmOutboundGroupSession}, this class decrypts the messages sent by the outbound side.
 *
 * <br><br>Detailed implementation guide is available at <a href="http://matrix.org/docs/guides/e2e_implementation.html">Implementing End-to-End Encryption in Matrix clients</a>.
 */
public class OlmInboundGroupSession extends CommonSerializeUtils implements Serializable {
    private static final long serialVersionUID = -772028491251653253L;
    private static final String LOG_TAG = "OlmInboundGroupSession";

    /** Session Id returned by JNI.<br>
     * This value uniquely identifies the native inbound group session instance.
     */
    private transient long mNativeId;

    /**
     * Result in {@link #decryptMessage(String)}
     */
    static class DecryptMessageResult {
        /** decrypt message **/
        public String mDecryptedMessage;

        /** decrypt index **/
        public long mIndex;
    }

    /**
     * Exception triggered in {@link #decryptMessage(String)}
     */
    static public class DecryptMessageException extends Exception {
        public DecryptMessageException(String message) {
            super(message);
        }
    }

    /**
     * Constructor.<br>
     * Create and save a new native session instance ID and start a new inbound group session.
     * The session key parameter is retrieved from an outbound group session
     * See {@link #createNewSession()} and {@link #initInboundGroupSession(String)}
     * @param aSessionKey session key
     * @throws OlmException constructor failure
     */
    public OlmInboundGroupSession(String aSessionKey) throws OlmException {
        if(createNewSession()) {
            if (0 != initInboundGroupSession(aSessionKey)) {
                releaseSession();// prevent memory leak before throwing
                throw new OlmException(OlmException.EXCEPTION_CODE_INIT_INBOUND_GROUP_SESSION,OlmException.EXCEPTION_MSG_INIT_INBOUND_GROUP_SESSION);
            }
        } else {
            throw new OlmException(OlmException.EXCEPTION_CODE_CREATE_INBOUND_GROUP_SESSION, OlmException.EXCEPTION_MSG_NEW_INBOUND_GROUP_SESSION);
        }
    }

    /**
     * Release native session and invalid its JAVA reference counter part.<br>
     * Public API for {@link #releaseSessionJni()}.
     */
    public void releaseSession(){
        releaseSessionJni();
        mNativeId = 0;
    }

    /**
     * Destroy the corresponding OLM inbound group session native object.<br>
     * This method must ALWAYS be called when this JAVA instance
     * is destroyed (ie. garbage collected) to prevent memory leak in native side.
     * See {@link #createNewSessionJni()}.
     */
    private native void releaseSessionJni();

    /**
     * Create and save the session native instance ID.<br>
     * To be called before any other API call.
     * @return true if init succeed, false otherwise.
     */
    private boolean createNewSession() {
        mNativeId = createNewSessionJni();
        return (0 != mNativeId);
    }

    /**
     * Create the corresponding OLM inbound group session in native side.<br>
     * Do not forget to call {@link #releaseSession()} when JAVA side is done.
     * @return native session instance identifier (see {@link #mNativeId})
     */
    private native long createNewSessionJni();

    /**
     * Return true the object resources have been released.<br>
     * @return true the object resources have been released
     */
    public boolean isReleased() {
        return (0 == mNativeId);
    }

    /**
     * Start a new inbound group session.<br>
     * The session key parameter is retrieved from an outbound group session
     * see {@link OlmOutboundGroupSession#sessionKey()}
     * @param aSessionKey session key
     * @return 0 if operation succeed, -1 otherwise
     */
    private int initInboundGroupSession(String aSessionKey) {
        int retCode = -1;

        if(TextUtils.isEmpty(aSessionKey)){
            Log.e(LOG_TAG, "## initInboundGroupSession(): invalid session key");
        } else {
            try {
                retCode = initInboundGroupSessionJni(aSessionKey.getBytes("UTF-8"));
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initInboundGroupSession() failed " + e.getMessage());
            }
        }

        return retCode;
    }

    private native int initInboundGroupSessionJni(byte[] aSessionKeyBuffer);

    /**
     * Retrieve the base64-encoded identifier for this inbound group session.
     * @return the session ID if operation succeed, null otherwise
     */
    public String sessionIdentifier() {
        try {
            return new String(sessionIdentifierJni(), "UTF-8");
        } catch (Exception e) {
            Log.e(LOG_TAG, "## sessionIdentifier() failed " + e.getMessage());
        }

        return null;
    }

    private native byte[] sessionIdentifierJni();

    /**
     * Decrypt the message passed in parameter.<br>
     * In case of error, null is returned and an error message description is provided in aErrorMsg.
     * @param aEncryptedMsg the message to be decrypted
     * @return the decrypted message information
     * @exception DecryptMessageException if there is an error while
     */
    public DecryptMessageResult decryptMessage(String aEncryptedMsg) throws DecryptMessageException {
        DecryptMessageResult result = new DecryptMessageResult();

        StringBuffer errorMsg = new StringBuffer();
        try {
            byte[] decryptedMessageBuffer = decryptMessageJni(aEncryptedMsg.getBytes("UTF-8"), result, errorMsg);

            if (null != decryptedMessageBuffer) {
                result.mDecryptedMessage = new String(decryptedMessageBuffer, "UTF-8");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## decryptMessage() failed " + e.getMessage());
            errorMsg.append(e.getMessage());
        }

        // check if there is an error while decrypting
        if (0 != errorMsg.length()) {
            throw new DecryptMessageException(errorMsg.toString());
        }

        return result;
    }

    private native byte[] decryptMessageJni(byte[] aEncryptedMsg, DecryptMessageResult aDecryptMessageResult, StringBuffer aErrorMsg);

    //==============================================================================================================
    // Serialization management
    //==============================================================================================================

    /**
     * Kick off the serialization mechanism.
     * @param aOutStream output stream for serializing
     * @throws IOException exception
     */
    private void writeObject(ObjectOutputStream aOutStream) throws IOException {
        serialize(aOutStream);
    }

    /**
     * Kick off the deserialization mechanism.
     * @param aInStream input stream
     * @throws IOException exception
     * @throws ClassNotFoundException exception
     */
    private void readObject(ObjectInputStream aInStream) throws IOException, ClassNotFoundException {
        deserialize(aInStream);
    }

    /**
     * Return the current inbound group session as a base64 serialized string.<br>
     * The session is serialized and encrypted with aKey.
     * In case of failure, an error human readable
     * description is provide in aErrorMsg.
     * @param aKey encryption key
     * @param aErrorMsg error message description
     * @return pickled base64 string if operation succeed, null otherwise
     */
    @Override
    protected String serialize(String aKey, StringBuffer aErrorMsg) {
        String pickleRetValue = null;

        // sanity check
        if(null == aErrorMsg) {
            Log.e(LOG_TAG,"## serialize(): invalid parameter - aErrorMsg=null");
        } else if(TextUtils.isEmpty(aKey)) {
            aErrorMsg.append("Invalid input parameters in serialize()");
        } else {
            aErrorMsg.setLength(0);
            try {
                pickleRetValue = new String(serializeJni(aKey.getBytes("UTF-8"), aErrorMsg), "UTF-8");
            } catch (Exception e) {
                Log.e(LOG_TAG, "## serialize() failed " + e.getMessage());
                aErrorMsg.append(e.getMessage());
            }
        }

        return pickleRetValue;
    }
    /**
     * JNI counter part of {@link #serialize(String, StringBuffer)}.
     * @param aKey encryption key
     * @param aErrorMsg error message description
     * @return pickled base64 string if operation succeed, null otherwise
     */
    private native byte[] serializeJni(byte[] aKey, StringBuffer aErrorMsg);

    /**
     * Loads an account from a pickled base64 string.<br>
     * See {@link #serialize(String, StringBuffer)}
     * @param aSerializedData pickled account in a base64 string format
     * @param aKey key used to encrypted
     */
    @Override
    protected void deserialize(String aSerializedData, String aKey) throws IOException {
        if (!createNewSession()) {
            throw new OlmException(OlmException.EXCEPTION_CODE_INIT_ACCOUNT_CREATION,OlmException.EXCEPTION_MSG_INIT_ACCOUNT_CREATION);
        }

        StringBuffer errorMsg = new StringBuffer();

        try {
            String jniError;
            if (TextUtils.isEmpty(aSerializedData) || TextUtils.isEmpty(aKey)) {
                Log.e(LOG_TAG, "## deserialize(): invalid input parameters");
                errorMsg.append("invalid input parameters");
            } else if (null != (jniError = deserializeJni(aSerializedData.getBytes("UTF-8"), aKey.getBytes("UTF-8")))) {
                errorMsg.append(jniError);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## deserialize() failed " + e.getMessage());
            errorMsg.append(e.getMessage());
        }

        if (errorMsg.length() > 0) {
            releaseSession();
            throw new OlmException(OlmException.EXCEPTION_CODE_ACCOUNT_DESERIALIZATION, String.valueOf(errorMsg));
        }
    }

    /**
     * JNI counter part of {@link #deserialize(String, String)}.
     * @param aSerializedData pickled session in a base64 string format
     * @param aKey key used to encrypted in {@link #serialize(String, StringBuffer)}
     * @return null if operation succeed, an error message if operation failed
     */
    private native String deserializeJni(byte[] aSerializedData, byte[] aKey);
}
