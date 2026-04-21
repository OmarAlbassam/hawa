import "./PageSkeleton.css";

const PageSkeleton = (): React.JSX.Element => {
  return (
    <div className="page-skeleton" role="status" aria-busy="true" aria-label="Loading page">
      <div className="page-skeleton-block page-skeleton-block--title" />
      <div className="page-skeleton-block page-skeleton-block--subtitle" />
      <div className="page-skeleton-grid">
        <div className="page-skeleton-block page-skeleton-block--card" />
        <div className="page-skeleton-block page-skeleton-block--card" />
        <div className="page-skeleton-block page-skeleton-block--card" />
      </div>
      <div className="page-skeleton-block page-skeleton-block--panel" />
    </div>
  );
};

export default PageSkeleton;
