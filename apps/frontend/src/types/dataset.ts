export type DatasetValidationErrorCode =
  | "MISSING_COLUMN"
  | "EMPTY_ROW"
  | "INVALID_LANGUAGE"
  | "TOO_MANY_ROWS"
  | "NO_DATA_ROWS"
  | "MALFORMED_CSV"
  | "EMPTY_FILE";

export interface PasteColumnMapping {
  text: string | null;
  url: string | null;
  language: string | null;
}

export interface PastePreview {
  rawText: string;
  headers: string[];
  previewRows: string[][];
  mapping: PasteColumnMapping;
}

export interface DatasetValidationError {
  code: DatasetValidationErrorCode;
  field: string | null;
  message: string;
}

export interface DatasetValidationErrorResponse {
  status: 400;
  message: string;
  timestamp: string;
  errors: DatasetValidationError[];
}

export class DatasetValidationFailed extends Error {
  readonly errors: DatasetValidationError[];

  constructor(errors: DatasetValidationError[]) {
    super("Dataset validation failed");
    this.name = "DatasetValidationFailed";
    this.errors = errors;
  }
}
