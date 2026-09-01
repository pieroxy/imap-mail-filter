package net.pieroxy.imf.utils;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.mail.util.MimeMessageParser;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MailTools {
    /**
     * Sérialise le message brut (headers + corps), sans jamais le marquer \Seen comme effet de
     * bord — utilisé par les vérifications DKIM/DMARC, qui ont besoin du message tel que reçu
     * pour recalculer une signature/un hash de corps.
     * <p>
     * Pour un vrai message IMAP, lire le contenu déclenche côté javax.mail un FETCH BODY[] ; la
     * propriété de session "mail.imap.peek" ne s'applique PAS à ce chemin (vérifié
     * empiriquement : elle ne couvre que le préchargement automatique à l'ouverture du dossier,
     * pas une lecture de contenu ad-hoc comme {@code writeTo()}) — {@link IMAPMessage#setPeek}
     * posé sur CE message précis, juste avant la lecture, est le seul mécanisme qui fonctionne.
     * Sans ça, n'importe quel message inspecté (matché ou non par une règle) se retrouverait
     * silencieusement marqué lu.
     */
    public static byte[] readRawMessageWithoutMarkingSeen(Message message) throws MessagingException, IOException {
        if (message instanceof IMAPMessage) {
            ((IMAPMessage) message).setPeek(true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toByteArray();
    }

    public static String getFrom(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Address a : from) {
            if (sb.length()>1) sb.append(" ");
            sb.append(getNiceMailAddress(a));
        }
        return sb.toString();
    }

    /** Description du From pour les logs : ne lève jamais, retourne un texte de repli sinon. */
    public static String describeFromSafely(Message message) {
        try {
            String from = getFrom(message);
            return from.isEmpty() ? "(unknown)" : from;
        } catch (MessagingException e) {
            return "?";
        }
    }

    public static String getNiceMailAddress(Address address) throws MessagingException {
        if (address instanceof InternetAddress) {
            InternetAddress ia = (InternetAddress) address;
            return ia.getPersonal() + " <" + ia.getAddress() + ">";
        } else {
            return address.toString();
        }
    }

    public static String getMailAddress(Address address) throws MessagingException {
        if (address instanceof InternetAddress) {
            InternetAddress ia = (InternetAddress) address;
            return  ia.getAddress();
        } else {
            return address.toString();
        }
    }

    public static Long getNextUid(Message m) throws MessagingException {
        Folder f = m.getFolder();
        if (f instanceof IMAPFolder) {
            return ((IMAPFolder) f).getUIDNext();
        }
        return null;
    }

    public static String getPlainContent(MimeMessage message) throws Exception {
        return new MimeMessageParser(message).parse().getPlainContent();
    }

    public static CharSequence getPlainContent(Object content) throws Exception {
        if (content instanceof String) return (String)content;
        if (content instanceof MimeMessage) return getPlainContent((MimeMessage) content);
        if (content instanceof MimeMultipart) return getTextFromMimeMultipart((MimeMultipart) content);
        return String.valueOf(content);
    }

    private static String getTextFromMimeMultipart(
            MimeMultipart mimeMultipart)  throws MessagingException, IOException {
        String result = "";
        for (int i = 0; i < mimeMultipart.getCount(); i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                return result + "\n" + bodyPart.getContent(); // without return, same text appears twice in my tests
            }
            result += parseBodyPart(bodyPart);
        }
        return result;
    }

    private static String parseBodyPart(BodyPart bodyPart) throws MessagingException, IOException {
        if (bodyPart.isMimeType("text/html")) {
            return "\n" + bodyPart.getContent().toString();
        }
        if (bodyPart.getContent() instanceof MimeMultipart){
            return getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
        }

        return "";
    }
}
