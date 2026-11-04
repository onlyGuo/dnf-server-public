package com.aiyi.game.dnfserver.security;

import com.aiyi.game.dnfserver.channel.ChannelService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class GameKeyServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void existingPlatformKeyStillProducesGameLoginAuthorization() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path key = temp.getRoot().toPath().resolve("private.pem");
        Files.write(key, pem.getBytes(StandardCharsets.US_ASCII));
        GameKeyService service = new GameKeyService(mock(ChannelService.class),
                temp.newFolder("game").toString(), temp.newFolder("game-data").toString(), key.toString());
        byte[] authorization = "game-login".getBytes(StandardCharsets.US_ASCII);

        byte[] encrypted = service.encryptForGame(authorization);

        Cipher decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decrypt.init(Cipher.DECRYPT_MODE, pair.getPublic());
        assertArrayEquals(authorization, decrypt.doFinal(encrypted));
        assertEquals(Boolean.TRUE, service.status().get("available"));
    }
}
