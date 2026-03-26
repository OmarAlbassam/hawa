import { API_BASE_URL } from "../config/api";
import type { AuthResponse, LoginRequest } from "../types/auth";

export async function login(data: LoginRequest): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Invalid email or password");
    }
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Login failed");
  }
  return response.json();
}

export async function logout(refreshToken: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Logout failed");
  }
}

export async function refreshTokens(refreshToken: string): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    throw new Error("Session expired. Please log in again.");
  }
  return response.json();
}
