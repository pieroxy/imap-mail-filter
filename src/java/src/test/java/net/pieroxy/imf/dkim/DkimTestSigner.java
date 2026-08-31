package net.pieroxy.imf.dkim;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

/**
 * Signe un message "à la main" (canonicalisation "simple", RFC 6376 §3.4.1 et §3.4.3),
 * indépendamment de la bibliothèque de vérification utilisée en prod ({@code
 * org.apache.james.jdkim}), pour que les tests de {@link DkimVerifier} valident cette
 * bibliothèque tierce contre une signature dont on maîtrise chaque octet — pas seulement un
 * aller-retour interne à la bibliothèque elle-même (qui masquerait un bug commun aux deux).
 */
public class DkimTestSigner {
  public static final class SignedMessage {
    public final String rawMessage;
    public final String publicKeyRecord;

    SignedMessage(String rawMessage, String publicKeyRecord) {
      this.rawMessage = rawMessage;
      this.publicKeyRecord = publicKeyRecord;
    }
  }

  /** headers doit préserver l'ordre d'insertion (ex: LinkedHashMap). */
  public static SignedMessage sign(String selector, String domain, Map<String, String> headers, String body) throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    KeyPair keyPair = keyGen.generateKeyPair();

    String bodyHash = base64(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    String signedHeaderNames = String.join(":", headers.keySet());

    // Le tag "b=" est présent mais vide au moment de calculer la signature (RFC 6376 §3.7
    // étape 4), et le header DKIM-Signature lui-même n'a PAS de CRLF final dans l'entrée signée.
    String dkimHeaderPrefix = "DKIM-Signature: v=1; a=rsa-sha256; c=simple/simple; d=" + domain + "; s=" + selector
            + "; h=" + signedHeaderNames + "; bh=" + bodyHash + "; b=";

    StringBuilder signingInput = new StringBuilder();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      signingInput.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
    }
    signingInput.append(dkimHeaderPrefix);

    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(keyPair.getPrivate());
    signer.update(signingInput.toString().getBytes(StandardCharsets.US_ASCII));
    String signature = base64(signer.sign());

    StringBuilder raw = new StringBuilder();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      raw.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
    }
    raw.append(dkimHeaderPrefix).append(signature).append("\r\n");
    raw.append("\r\n").append(body);

    String publicKeyRecord = "v=DKIM1; k=rsa; p=" + base64(keyPair.getPublic().getEncoded());
    return new SignedMessage(raw.toString(), publicKeyRecord);
  }

  private static String base64(byte[] data) {
    return Base64.getEncoder().encodeToString(data);
  }
}
