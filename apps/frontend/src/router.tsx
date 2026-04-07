import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import AdminRoute from "./components/AdminRoute/AdminRoute";
import AdminLayout from "./components/AdminLayout/AdminLayout";
import Login from "./pages/Login/Login";
import UserManagement from "./pages/Admin/UserManagement/UserManagement";
import SystemAnalytics from "./pages/Admin/SystemAnalytics/SystemAnalytics";
import ReportedReviews from "./pages/Admin/ReportedReviews/ReportedReviews";
import CompanyManagement from "./pages/Admin/CompanyManagement/CompanyManagement";
import BrandManagement from "./pages/Admin/BrandManagement/BrandManagement";

const AppRouter = () => (
  <BrowserRouter>
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <AdminLayout />
            </AdminRoute>
          }
        >
          <Route index element={<Navigate to="users" replace />} />
          <Route path="users" element={<UserManagement />} />
          <Route path="analytics" element={<SystemAnalytics />} />
          <Route path="companies" element={<CompanyManagement />} />
          <Route path="brands" element={<BrandManagement />} />
          <Route path="reviews" element={<ReportedReviews />} />
        </Route>
        <Route path="/" element={<Navigate to="/admin" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AuthProvider>
  </BrowserRouter>
);

export default AppRouter;
