package net.pieroxy.imf.dkim;

import org.apache.james.jdkim.DKIMVerifier;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.api.Result;
import org.apache.james.jdkim.exceptions.FailException;
import org.apache.james.jdkim.exceptions.PermFailException;
import org.apache.james.jdkim.exceptions.TempFailException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Vérifie la ou les signatures DKIM (RFC 6376) d'un message, en s'appuyant sur
 * {@code org.apache.james.jdkim} (parsing MIME, canonicalisation, cryptographie) plutôt que de
 * réimplémenter ce protocole nous-mêmes — contrairement au SPF, il n'existe pas de raccourci
 * "juste comparer des chaînes" : c'est de la vérification de signature RSA/Ed25519 sur des
 * données canonicalisées selon des règles précises, où une seule divergence d'implémentation
 * fait échouer silencieusement des signatures pourtant valides.
 * <p>
 * Un message peut porter plusieurs headers {@code DKIM-Signature} (plusieurs signataires) :
 * comme pour un "include" SPF, une seule signature valide suffit à rendre le message PASS
 * (RFC 6376 §6.1). En l'absence de toute signature valide, on retient le résultat le plus
 * "informatif" parmi tous les échecs (FAIL avant PERMERROR avant TEMPERROR, etc.) — voir
 * {@link #PRIORITY}.
 */
public class DkimVerifier {
  // Ordre de préférence quand aucune signature n'est valide : FAIL (signature présente et
  // active mais cryptographiquement invalide) est le signal le plus fort et le plus digne de
  // confiance, donc prioritaire sur une simple erreur d'évaluation (PERMERROR/TEMPERROR).
  private static final Result.Type[] PRIORITY = {
          Result.Type.FAIL, Result.Type.PERMERROR, Result.Type.TEMPERROR, Result.Type.POLICY, Result.Type.NEUTRAL, Result.Type.NONE
  };

  private final PublicKeyRecordRetriever publicKeyRecordRetriever;
  private final Logger defaultLogger = Logger.getLogger(DkimVerifier.class.getName());

  public DkimVerifier(PublicKeyRecordRetriever publicKeyRecordRetriever) {
    this.publicKeyRecordRetriever = publicKeyRecordRetriever;
  }

  /** @return le résultat DKIM pour le message brut (headers + corps, tel que reçu) lu depuis rawMessage. */
  public DkimResult verify(InputStream rawMessage) {
    return verify(rawMessage, defaultLogger);
  }

  /** Comme {@link #verify(InputStream)}, mais journalise (niveau FINE) le détail par signature sur le logger donné. */
  public DkimResult verify(InputStream rawMessage, Logger logger) {
    DKIMVerifier verifier = new DKIMVerifier(publicKeyRecordRetriever);
    // Repli utilisé seulement si getResults() ci-dessous ne donne rien d'exploitable : quand
    // verify() lève, le résultat précis par signature (ex: FAIL pour un bodyhash qui ne
    // correspond pas) est en général déjà enregistré dans getResults() malgré l'exception —
    // l'exception ne fait que signaler "aucune signature n'a été retenue comme valide".
    DkimResult exceptionFallback;
    try {
      verifier.verify(rawMessage);
      exceptionFallback = null;
    } catch (PermFailException e) {
      logger.fine(() -> "DKIM permanent failure: " + e.getMessage());
      exceptionFallback = DkimResult.PERMERROR;
    } catch (TempFailException e) {
      logger.fine(() -> "DKIM temporary failure: " + e.getMessage());
      exceptionFallback = DkimResult.TEMPERROR;
    } catch (FailException e) {
      // Sous-type non prévu ci-dessus (ex: CompositeFailException) : on ne sait pas si c'est
      // temporaire, donc on ne fait pas confiance au message par défaut (fail-closed).
      logger.fine(() -> "DKIM verification failed: " + e.getMessage());
      exceptionFallback = DkimResult.PERMERROR;
    } catch (IOException e) {
      // Rien n'a pu être lu : pas de résultats à consulter, on s'arrête ici.
      logger.log(Level.FINE, "Failed to read message for DKIM verification", e);
      return DkimResult.TEMPERROR;
    }

    List<Result> results = verifier.getResults();
    if (results != null) {
      for (Result result : results) {
        logger.fine(() -> "DKIM signature result: " + result.getHeaderTextWithReason());
      }
    }

    if (verifier.hasAnyValidSignature()) {
      return DkimResult.PASS;
    }
    if (results == null || results.isEmpty()) {
      if (exceptionFallback != null) return exceptionFallback;
      logger.fine(() -> "No DKIM-Signature header on message");
      return DkimResult.NONE;
    }
    for (Result.Type type : PRIORITY) {
      if (results.stream().anyMatch(r -> r.getResultType() == type)) {
        return DkimResult.valueOf(type.name());
      }
    }
    return exceptionFallback != null ? exceptionFallback : DkimResult.NONE;
  }
}
