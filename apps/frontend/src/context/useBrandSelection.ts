import { useContext } from "react";
import { BrandSelectionContext } from "./brandSelectionContextDef";
import type { BrandSelectionContextValue } from "./brandSelectionContextDef";

export function useBrandSelection(): BrandSelectionContextValue {
  const context = useContext(BrandSelectionContext);
  if (!context) {
    throw new Error(
      "useBrandSelection must be used within a BrandSelectionProvider"
    );
  }
  return context;
}
