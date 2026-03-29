import { useState, useCallback } from "react";
import type { AuthResponse, UserInfo } from "../types/auth";
import { AuthContext } from "./authContextDef";
import type { AuthState, AuthContextValue } from "./authContextDef";

function loadAuthState(): AuthState {
  const accessToken = localStorage.getItem("accessToken");
  const refreshToken = localStorage.getItem("refreshToken");
  const userJson = localStorage.getItem("user");
  let user: UserInfo | null = null;
  if (userJson) {
    try {
      user = JSON.parse(userJson);
    } catch {
      // ignore corrupt data
    }
  }
  return { accessToken, refreshToken, user };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>(loadAuthState);

  const setAuth = useCallback((response: AuthResponse) => {
    localStorage.setItem("accessToken", response.accessToken);
    localStorage.setItem("refreshToken", response.refreshToken);
    localStorage.setItem("user", JSON.stringify(response.user));
    setState({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      user: response.user,
    });
  }, []);

  const clearAuth = useCallback(() => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("user");
    setState({ accessToken: null, refreshToken: null, user: null });
  }, []);

  const value: AuthContextValue = {
    ...state,
    isAuthenticated: !!state.accessToken,
    setAuth,
    clearAuth,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
