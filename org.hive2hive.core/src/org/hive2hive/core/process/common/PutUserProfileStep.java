package org.hive2hive.core.process.common;

import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;

import net.tomp2p.futures.FutureDHT;

import org.apache.log4j.Logger;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.encryption.EncryptedNetworkContent;
import org.hive2hive.core.encryption.EncryptionUtil.AES_KEYLENGTH;
import org.hive2hive.core.encryption.H2HEncryptionUtil;
import org.hive2hive.core.encryption.PasswordUtil;
import org.hive2hive.core.encryption.UserPassword;
import org.hive2hive.core.log.H2HLoggerFactory;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.messages.direct.response.ResponseMessage;
import org.hive2hive.core.process.ProcessStep;
import org.hive2hive.core.process.PutProcessStep;

/**
 * Generic process step to encrypt the {@link: UserProfile} and add it to the DHT
 * 
 * @author Nico
 * 
 */
public class PutUserProfileStep extends PutProcessStep {

	private final static Logger logger = H2HLoggerFactory.getLogger(PutUserProfileStep.class);

	private final UserProfile profile;
	private final ProcessStep next;
	private final UserPassword password;

	public PutUserProfileStep(UserProfile profile, UserProfile previousVersion, UserPassword password,
			ProcessStep next) {
		super(previousVersion);
		this.profile = profile;
		this.next = next;
		this.password = password;
	}

	@Override
	public void start() {
		logger.debug("Encrypting UserProfile with 256bit AES key from password");
		try {
			SecretKey encryptionKey = PasswordUtil
					.generateAESKeyFromPassword(password, AES_KEYLENGTH.BIT_256);
			EncryptedNetworkContent encryptedUserProfile = H2HEncryptionUtil.encryptAES(profile, encryptionKey);
			logger.debug("Putting UserProfile into the DHT");
			put(profile.getUserId(), H2HConstants.USER_PROFILE, encryptedUserProfile);
		} catch (InvalidKeySpecException | DataLengthException | IllegalStateException
				| InvalidCipherTextException e) {
			// TODO Handle exceptions and rollback
			e.printStackTrace();
			getProcess().rollBack(e.getMessage());
		}
	}

	@Override
	public void rollBack() {
		super.rollBackPut(profile.getUserId(), H2HConstants.USER_PROFILE);
	}

	@Override
	protected void handleMessageReply(ResponseMessage asyncReturnMessage) {
		// does not send any message
	}

	@Override
	protected void handlePutResult(FutureDHT future) {
		if (future.isSuccess()) {
			getProcess().nextStep(next);
		} else {
			logger.error("Error occurred while putting user profile into DHT. Starting rollback");
			getProcess().rollBack("UserProfile could not be put");
		}
	}

	@Override
	protected void handleGetResult(FutureDHT future) {
		// does not perform a get
	}

	@Override
	protected void handleRemovalResult(FutureDHT future) {
		// no removal used
	}

}
