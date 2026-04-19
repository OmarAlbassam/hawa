import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import AdminRoute from "./components/AdminRoute/AdminRoute";
import MarketingRoute from "./components/MarketingRoute/MarketingRoute";
import AdminLayout from "./components/AdminLayout/AdminLayout";
import MarketingLayout from "./components/MarketingLayout/MarketingLayout";
import Login from "./pages/Login/Login";
import UserManagement from "./pages/Admin/UserManagement/UserManagement";
import SystemAnalytics from "./pages/Admin/SystemAnalytics/SystemAnalytics";
import ReportedReviews from "./pages/Admin/ReportedReviews/ReportedReviews";
import CompanyManagement from "./pages/Admin/CompanyManagement/CompanyManagement";
import BrandManagement from "./pages/Admin/BrandManagement/BrandManagement";
import Dashboard from "./pages/Dashboard/Dashboard";
import BrandList from "./pages/Brands/BrandList";
import BrandDetail from "./pages/Brands/BrandDetail";
import ReportList from "./pages/Reports/ReportList";
import NotFound from "./pages/NotFound/NotFound";

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
        <Route
          element={
            <MarketingRoute>
              <MarketingLayout />
            </MarketingRoute>
          }
        >
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/brands" element={<BrandList />} />
          <Route path="/brands/:brandId" element={<BrandDetail />} />
          <Route path="/reports" element={<ReportList />} />
        </Route>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </AuthProvider>
  </BrowserRouter>
);

export default AppRouter;
