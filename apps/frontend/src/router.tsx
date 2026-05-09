import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { BrandSelectionProvider } from "./context/BrandSelectionContext";
import AdminRoute from "./components/AdminRoute/AdminRoute";
import MarketingRoute from "./components/MarketingRoute/MarketingRoute";
import AdminLayout from "./components/AdminLayout/AdminLayout";
import MarketingLayout from "./components/MarketingLayout/MarketingLayout";
import PageSkeleton from "./components/PageSkeleton/PageSkeleton";

const Login = lazy(() => import("./pages/Login/Login"));
const UserManagement = lazy(() => import("./pages/Admin/UserManagement/UserManagement"));
const SystemAnalytics = lazy(() => import("./pages/Admin/SystemAnalytics/SystemAnalytics"));
const ReportedReviews = lazy(() => import("./pages/Admin/ReportedReviews/ReportedReviews"));
const CompanyManagement = lazy(() => import("./pages/Admin/CompanyManagement/CompanyManagement"));
const BrandManagement = lazy(() => import("./pages/Admin/BrandManagement/BrandManagement"));
const Dashboard = lazy(() => import("./pages/Dashboard/Dashboard"));
const BrandList = lazy(() => import("./pages/Brands/BrandList"));
const BrandDetail = lazy(() => import("./pages/Brands/BrandDetail"));
const ReportList = lazy(() => import("./pages/Reports/ReportList"));
const StartAnalysis = lazy(() => import("./pages/Analysis/StartAnalysis"));
const ReportStatus = lazy(() => import("./pages/Analysis/ReportStatus"));
const PostsList = lazy(() => import("./pages/Analysis/PostsList"));
const NotFound = lazy(() => import("./pages/NotFound/NotFound"));

const AppRouter = () => (
  <BrowserRouter>
    <AuthProvider>
      <Suspense fallback={<PageSkeleton />}>
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
                <BrandSelectionProvider>
                  <MarketingLayout />
                </BrandSelectionProvider>
              </MarketingRoute>
            }
          >
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/brands" element={<BrandList />} />
            <Route path="/brands/:brandId" element={<BrandDetail />} />
            <Route path="/analyze" element={<StartAnalysis />} />
            <Route path="/reports" element={<ReportList />} />
            <Route path="/reports/:reportId" element={<ReportStatus />} />
            <Route path="/reports/:reportId/posts" element={<PostsList />} />
          </Route>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </AuthProvider>
  </BrowserRouter>
);

export default AppRouter;
