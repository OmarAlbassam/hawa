export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserInfo;
}

export interface UserInfo {
  userId: number;
  email: string;
  role: "ADMIN" | "MARKETING_USER";
  firstName: string;
  lastName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  companyId: number;
  role: "ADMIN" | "MARKETING_USER";
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface ApiError {
  status: number;
  message: string;
  timestamp: string;
}
