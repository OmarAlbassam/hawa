import { createContext } from "react";
import type { AuthResponse, UserInfo } from "../types/auth";

export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserInfo | null;
}

export interface AuthContextValue extends AuthState {
  isAuthenticated: boolean;
  setAuth: (response: AuthResponse) => void;
  clearAuth: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
