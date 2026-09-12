import client from "./internal/httpClient";

export function login(account: string, password: string) {
  return client.post("/api/v1/auth/login/password", {
    account,
    password: password,
  });
}

export function logout() {
  return client.post("/api/v1/auth/logout", {});
}

export function loginLdap(account: string, password: string) {
  return client.post("/api/v1/auth/login/ldap", {
    username: account,
    password: password,
  });
}
