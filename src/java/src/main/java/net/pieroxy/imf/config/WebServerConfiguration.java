package net.pieroxy.imf.config;

public class WebServerConfiguration {
  private int httpPort;
  private String address;
  /** Key into credentials.json's top-level "credentials" map — see {@link CredentialsResolver}. */
  private String credentials;

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getCredentials() {
    return credentials;
  }

  public void setCredentials(String credentials) {
    this.credentials = credentials;
  }
}
