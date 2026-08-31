package net.pieroxy.imf.spf;

import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implémentation de {@link SpfDnsResolver} basée sur dnsjava, utilisant le résolveur DNS
 * du système (typiquement celui configuré dans /etc/resolv.conf).
 */
public class DnsJavaSpfDnsResolver implements SpfDnsResolver {
  private final Resolver resolver;

  public DnsJavaSpfDnsResolver() {
    this(Duration.ofSeconds(5));
  }

  public DnsJavaSpfDnsResolver(Duration timeout) {
    try {
      SimpleResolver simpleResolver = new SimpleResolver();
      simpleResolver.setTimeout(timeout);
      this.resolver = simpleResolver;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Could not initialize DNS resolver", e);
    }
  }

  @Override
  public List<String> lookupTxt(String domain) throws SpfDnsException {
    List<String> result = new ArrayList<>();
    for (Record record : lookup(domain, Type.TXT)) {
      if (record instanceof TXTRecord) {
        result.add(String.join("", ((TXTRecord) record).getStrings()));
      }
    }
    return result;
  }

  @Override
  public List<String> lookupA(String domain) throws SpfDnsException {
    List<String> result = new ArrayList<>();
    for (Record record : lookup(domain, Type.A)) {
      if (record instanceof ARecord) {
        result.add(((ARecord) record).getAddress().getHostAddress());
      }
    }
    return result;
  }

  @Override
  public List<String> lookupAaaa(String domain) throws SpfDnsException {
    List<String> result = new ArrayList<>();
    for (Record record : lookup(domain, Type.AAAA)) {
      if (record instanceof AAAARecord) {
        result.add(((AAAARecord) record).getAddress().getHostAddress());
      }
    }
    return result;
  }

  @Override
  public List<String> lookupMx(String domain) throws SpfDnsException {
    List<String> result = new ArrayList<>();
    for (Record record : lookup(domain, Type.MX)) {
      if (record instanceof MXRecord) {
        result.add(((MXRecord) record).getTarget().toString(true));
      }
    }
    return result;
  }

  private Record[] lookup(String domain, int type) throws SpfDnsException {
    try {
      Lookup lookup = new Lookup(domain, type);
      lookup.setResolver(resolver);
      Record[] answers = lookup.run();
      int result = lookup.getResult();
      // HOST_NOT_FOUND (NXDOMAIN) et TYPE_NOT_FOUND (NODATA) sont des réponses DNS normales
      // signifiant "rien de ce type ici", pas des échecs : on renvoie une liste vide.
      if (result == Lookup.HOST_NOT_FOUND || result == Lookup.TYPE_NOT_FOUND) {
        return new Record[0];
      }
      if (result != Lookup.SUCCESSFUL) {
        throw new SpfDnsException("DNS lookup of " + type + " " + domain + " failed: " + lookup.getErrorString());
      }
      return answers != null ? answers : new Record[0];
    } catch (TextParseException e) {
      throw new SpfDnsException("Invalid domain name: " + domain, e);
    }
  }
}
