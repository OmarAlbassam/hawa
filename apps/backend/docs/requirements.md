# Hawa - Extracted Requirements

Source: Report.pdf (IS498 Capstone Project Report — Sentiment Analysis using LLMs)

---

## Use Cases

> The report does not assign IDs to use cases. IDs below (UC-01 through UC-09) are assigned in the order the use case description tables appear in the report (Tables 4-12). Three additional use cases appear in the Use Case Diagram (Figure 2, p.17) but have no description tables: **View Result History**, **Create User Account**, and **View Reported Reviews** — see Extraction Notes.

### UC-01: Start Analysis

- **Actor(s):** Marketing Team User
- **Scenario:** Marketing team user starts an analysis
- **Triggering Event:** Marketing team user click the "Start Analysis" button on the dashboard
- **Brief Description:** This use case allows marketing team employees to initiate automated sentiment analysis on social media posts related to their brand. The system collects posts from specified sources, analyzes them using LLM-based processing to extract sentiment, emotions, and aspects, then aggregates the results into a comprehensive brand status report.
- **Related Use Cases:** None
- **Preconditions:**
  - System must have connectivity to social media API
  - LLM Analyzer service must be operational
- **Postconditions:**
  - Analysis report is generated and stored in the database
  - Dashboard displays "Analysis Complete" status message
  - "View Results" button is enabled to the user
- **Main Flow:**

  | Marketing Team User | System |
  |---|---|
  | 1. Clicks "Start Analysis" button | 1.1 Displays analysis config form to user |
  | 2. Fills in config form | 2.1 Validate input parameters |
  | 3. Submits analysis request | 3.1 Retrieve brand details and keywords |
  | 4. Wait for analysis to complete | 3.2 Display "Analysis Started" message |
  | 5. View "Analysis Complete" message | 4.1 Collect posts from social media |
  | 6. Clicks "View Results" to access report | 4.2 Send posts to LLM analyzer |
  | | 4.3 Receive results and generate report |
  | | 5.1 Display "Analysis Complete" message |
  | | 6.1 Retrieve and display complete report |

- **Exception Conditions:**
  - E2.1 Invalid Input
  - E4.1 Social media API failure
  - E4.2 LLM analyzer unavailable

*(Source: Table 4, p.18)*

---

### UC-02: View Result

- **Actor(s):** Marketing Team User
- **Scenario:** Marketing Team User views the results of a sentiment analysis report (either newly generated or previously saved)
- **Triggering Event:** User clicks on a completed report from View Report History or dashboard
- **Brief Description:** This use case allows the Marketing Team User to view an overview and summary of a completed sentiment analysis report. The overview displays aggregated sentiment scores, emotion distribution, aspect breakdown, and overall confidence levels for a brand's analyzed social media posts. From this view, users can navigate to more detailed views of aspects, status indicators, and individual posts.
- **Related Use Cases:** View Aspects, View Status Indicator, View Posts, View Report History, Generate Report
- **Preconditions:**
  - The Marketing Team User is authenticated and logged in.
  - At least one analysis report must exist for the selected brand
  - The report status must be "finished" (not "processing" or "error")
- **Postconditions:**
  - User successfully views the analysis report overview and summary
  - User can navigate to related detailed views (View Aspects, View Status Indicator, View Posts)
- **Main Flow:**

  | Marketing Team User | System |
  |---|---|
  | 1. Clicks on a completed report from the report list or dashboard | 2. Retrieves report data from the database |
  | 4. Reviews displayed report summary | 3. Displays the report overview |
  | 5. Optionally navigates to View Aspects, View Status Indicator, or View Posts for detailed analysis | |
  | 6. Optionally, clicks on Generate Report | 7. Handled by Generate Report use case |

- **Exception Conditions:**
  - E2 No Analytics Available
  - E3 Analysis Failed
  - E4 Analysis Still Processing

*(Source: Table 5, p.19)*

---

### UC-03: View Posts

- **Actor(s):** Marketing Team User
- **Scenario:** Marketing Team User views individual analyzed posts from a sentiment analysis report
- **Triggering Event:** User clicks on the posts card/section within the View Result dashboard
- **Brief Description:** This use case allows the Marketing Team User to view detailed information about individual social media posts that were analyzed in a report. Posts are displayed in a paginated table format showing post text, sentiment score, dominant emotion, detected aspect, and confidence level. Users can filter and sort posts by various criteria including sentiment range, emotion type, aspect type, confidence level, and date range.
- **Related Use Cases:** View Result, Report Inaccurate Analysis
- **Preconditions:**
  - The Marketing Team User has accessed View Result page
  - Post analysis is complete with sentiment scores, emotions, aspects, and confidence
- **Postconditions:**
  - User successfully views the list of analyzed posts
  - User can filter and sort posts by multiple criteria
  - User returns to the View Result dashboard
  - User can report inaccurate analysis for individual posts
- **Main Flow:**

  | Marketing Team User | System |
  |---|---|
  | 1. Clicks on the posts card | 2. Retrieves post & analysis data from database |
  | 4. Reviews displayed posts | 3. Displays posts in paginated table format: Post text, Sentiment score, Dominant emotion, Detected aspect, Confidence level. |
  | 5. Optionally apply filters and sorting | 6. Updates view to reflect filters and sorting |
  | 7. Optionally report inaccurate analysis | 8. Handled by Report Inaccurate Analysis |

- **Exception Conditions:**
  - E2 Database Connection Failure
  - E5 No Posts Match Filter Criteria

*(Source: Table 6, p.20)*

---

### UC-04: View Aspects

- **Actor(s):** Marketing Team User
- **Scenario:** Team User views the aspect breakdown of a sentiment analysis report
- **Triggering Event:** User clicks on the aspects card/section within the View Result dashboard
- **Brief Description:** This use case allows the Marketing Team User to view a detailed breakdown of aspects detected in analyzed social media posts. The aspects view displays aspect types ranked by frequency (number of posts mentioning each aspect), along with corresponding sentiment scores, emotion distribution per aspect, and post counts. This enables marketing teams to identify which specific areas (product, service, pricing, delivery, customer service, etc.) are driving customer sentiment and where attention is needed.
- **Related Use Cases:** View Result
- **Preconditions:**
  - The Marketing Team User has accessed View Result page
  - The report contains complete analyzed posts with aspects
- **Postconditions:**
  - User successfully views the aspect analysis breakdown
  - User can filter aspects by aspect type
  - User returns to the View Result overview
- **Main Flow:**

  | Marketing Team User | System |
  |---|---|
  | 1. Clicks on the aspects card | 2. Retrieves aspect data from database |
  | 4. Reviews the displayed aspect analysis and statistics | 3. Displays the aspect analysis data: a. Aspect name, b. Posts included in each aspect, c. Average score per aspect, d. Emotion distribution |
  | 5. Returns to View Result overview | |

- **Exception Conditions:**
  - E1 No Aspects Detected in Analysis
  - E2 Database Connection Failure

*(Source: Table 7, p.21)*

---

### UC-05: View Status Indicator

- **Actor(s):** Marketing Team User
- **Scenario:** Marketing Team User views the overall brand status indicator
- **Triggering Event:** User clicks on the status indicator card/section within the View Result dashboard
- **Brief Description:** This use case allows the Marketing Team User to view the aggregated brand status indicator, which provides a comprehensive overview of the overall sentiment health. The status indicator displays a color-coded sentiment score, emotion analytics including the dominant emotion and top emotions breakdown, confidence metrics, emotion diversity, and an LLM-generated interpretive comment summarizing the analysis findings.
- **Related Use Cases:** View Result
- **Preconditions:**
  - The Marketing Team User has accessed View Result page
  - Aggregated calculations for the report are complete
  - The report contains analyzed posts
- **Postconditions:**
  - User successfully views the brand status indicator metrics
  - User returns to the View Result overview
- **Main Flow:**

  | Marketing Team User | System |
  |---|---|
  | 1. Clicks on the status indicator card | 2. Retrieves report score data from database |
  | 4. Reviews the displayed status metrics and insights | 3. Displays the status indicator data: a. Color-coded status, b. Sentiment breakdown, c. Dominant emotion, d. Top 3 emotions, e. Diversity score, f. Confidence level, g. Summary |
  | 5. Returns to View Result overview | |

- **Exception Conditions:**
  - E2 Database Connection Failure

*(Source: Table 8, p.22)*

---


*(Source: Table 9, p.23)*

---

### UC-07: View Analytics

- **Actor(s):** Admin
- **Scenario:** The Admin accesses the analytics dashboard to monitor system usage and brand analysis activity across the system.
- **Triggering Event:** The Admin navigates to the "View Analytics" page from the admin dashboard.
- **Brief Description:** This use case allows the Admin to view comprehensive analytics about system usage and brand analysis activity. The analytics provide insights into LLM utilization, post analysis trends, brand performance metrics, and other key indicators. The Admin can apply various filters to refine the view and focus on specific time periods, companies, brands, or other criteria.
- **Related Use Cases:** None
- **Preconditions:**
  - The Admin is authenticated and logged into the system.
- **Postconditions:**
  - The system displays the latest analytical results retrieved from the database.
  - The analytics dashboard remains accessible for further exploration and filtering.
- **Main Flow:**

  | Admin | System |
  |---|---|
  | 1. Navigates to "View Analytics" | 2. Retrieves all analytics from the database |
  | 4. Reviews displayed analytics | 3. Populates analytics dashboard |
  | 5. Optionally, applies filter criteria (date range, brands, etc.) | 6. Applies filter function on the data |
  | 8. Reviews filtered analytics | 7. Displays criteria-met data |

- **Exception Conditions:**
  - E2 No Analytics Available
  - E2 Database Connection Failure
  - E5 Invalid Filter Criteria

*(Source: Table 10, p.24)*

---

### UC-08: Upload Custom Dataset

- **Actor(s):** Marketing Team User
- **Scenario:** After starting a new analysis, the marketing team user may choose to upload a custom dataset instead of using the system's default data source. This dataset is then used as the input for sentiment analysis.
- **Triggering Event:** The user selects **"Start Analysis"**, and within the configuration options, clicks **"Upload Custom Dataset"**.
- **Brief Description:** This use case allows the marketing team user to upload a CSV or XLSX file containing posts, comments, or text data to be analyzed. The system validates the file type and size, uploads it, stores it, and makes it available for processing. The custom dataset replaces the standard social media data source for the analysis run.
- **Related Use Cases:** Start Analysis
- **Preconditions:**
  - The user is logged in and authorized.
  - The user has already initiated the **Start Analysis** use case.
  - The file is available on the user's device.
- **Postconditions:**
  - The uploaded dataset is stored in the system.
  - The dataset is linked to the analysis request.
  - The system is ready to run sentiment analysis using the custom uploaded data.
- **Main Flow:**

  **Actor's Actions:**
  - The user starts a new analysis.
  - The user selects "Upload Custom Dataset".
  - The user chooses a CSV/XLSX file and submits it.

  **System's Responses:**
  - The system displays analysis options, including dataset upload.
  - The system opens a file upload window or prompt.
  - The system validates the file type and size, uploads it, stores it, and confirms successful upload.

- **Exception Conditions:**
  - Invalid file format. System displays: "Unsupported file type. Please upload CSV or XLSX."
  - File too large. System displays: "File exceeds size limit."

*(Source: Table 11, p.25)*

---

### UC-09: Generate Report

- **Actor(s):** Marketing Team User
- **Scenario:** Marketing Team User generates a downloadable sentiment analysis report containing posts and their analysis results in CSV format.
- **Triggering Event:** Marketing Team User clicks the "Generate Report" button in the dashboard interface.
- **Brief Description:** This use case enables Marketing Team Users to generate comprehensive reports containing sentiment analysis results for social media posts. The system retrieves analyzed posts from the database, including sentiment scores, aspect analysis, and emotion detection, then compiles them into a structured CSV file format. The generated report provides a downloadable file that can be used for offline analysis, presentations to stakeholders, or integration with other business intelligence tools.
- **Related Use Cases:** View Result
- **Preconditions:**
  - At least one analysis must have been completed
  - The database must contain post data with analysis report
- **Postconditions:**
  - CSV file containing posts and their sentiment analysis is exported successfully
  - Download URL for the CSV file is created and returned to the user
- **Main Flow:**

  | Marketing Team User | System |
  |---|---|
  | 1. Clicks "Generate Report" button | 2. Fetches posts and reviews from database |
  | 6. Receive download URL | 3. Creates and formats a CSV file with report data |
  | | 4. Create a download URL |
  | | 5. Return URL to user |

- **Exception Conditions:**
  - E1 Invalid Report ID
  - E2 Database Connection Failure
  - E3 Empty Dataset
  - E4 CSV Service Failure
  - E5 Insufficient Permissions

*(Source: Table 12, p.26)*

---

## Functional Requirements

> The report does not assign IDs to functional requirements. They are listed as bullet points under category headings in Section 3.3 (p.15). IDs below (FR-01 through FR-12) are assigned sequentially in document order.

### FR-01: Collect Posts from Social Media

- **Category:** Data Handling
- **Description:** The system shall collect posts from a social media platform.

### FR-02: Upload Custom Datasets

- **Category:** Data Handling
- **Description:** The system shall allow uploading of custom datasets in CSV format.

### FR-03: Language Detection and Preprocessing

- **Category:** Data Handling
- **Description:** The system shall detect language (Arabic/English) and preprocess text for analysis.

### FR-04: Sentiment Scoring

- **Category:** Sentiment & Aspect Analysis
- **Description:** The system shall assign a score to represent sentiment (negative, neutral, positive).

### FR-05: Confidence Score

- **Category:** Sentiment & Aspect Analysis
- **Description:** The system shall provide a confidence score for each classification.

### FR-06: Aspect Detection

- **Category:** Sentiment & Aspect Analysis
- **Description:** The system shall detect main aspects such as product, service, delivery, and pricing.

### FR-07: Emotion Detection

- **Category:** Sentiment & Aspect Analysis
- **Description:** The system shall detect basic emotions including joy, anger, and sadness.

### FR-08: Dashboard with Trends

- **Category:** Dashboard & Reporting
- **Description:** The system shall provide a dashboard showing sentiment trends, aspect analysis, and emotion distribution.

### FR-09: Filtering

- **Category:** Dashboard & Reporting
- **Description:** The system shall allow filtering by date, sentiment type, aspect, and language.

### FR-10: Export Reports

- **Category:** Dashboard & Reporting
- **Description:** The system shall allow exporting reports in CSV format.

### FR-11: Role-Based Access

- **Category:** User Roles & Feedback
- **Description:** The system shall support role-based access (Admin, Marketing Team).

### FR-12: Feedback on Misclassified Posts

- **Category:** User Roles & Feedback
- **Description:** The system shall allow users to provide feedback on misclassified posts for model improvement.

---

## Extraction Notes

### Pages extracted from
- **p.3-4**: Table of Contents (structure reference)
- **p.5**: List of Figures / List of Tables
- **p.15**: Section 3.3 Functional Requirements (all 12 items)
- **p.16**: Section 3.4 Non-Functional Requirements (read but excluded per instructions — only functional requirements requested)
- **p.17**: Section 3.5 Use Case Diagram (Figure 2)
- **pp.18-26**: Section 3.5.2 Use Case Descriptions (Tables 4-12)

### Use cases present in diagram but missing description tables
The Use Case Diagram (Figure 2, p.17) shows three use cases that do **not** have corresponding description tables anywhere in the report:

1. **View Result History** — shown as a use case connected to Marketing Team User with an "includes" relationship from View Result. The list of tables mentions no table for this use case.
2. **Create User Account** — shown as an Admin use case in the diagram. No description table exists.
3. **View Reported Reviews** — shown as an Admin use case in the diagram. No description table exists.

### Items skipped
- **Non-Functional Requirements** (Section 3.4, p.16): Performance, Usability, Accuracy & Quality, Security, Reliability & Availability, Maintainability & Scalability — excluded because the request was for functional requirements only.
- **Prototyping & Feasibility Testing** (Section 3.5 / p.27): Contains a sample result table (Table 13) but is not a use case or functional requirement.

### Table extraction quality
- All use case description tables (Tables 4-12) extracted cleanly from the PDF text layer.
- The Flow of Activities in Tables 9 (Report Inaccurate Review) and 11 (Upload Custom Dataset) used a different format (narrative Actor's Actions / System's Responses) rather than the two-column table used in other use cases. These were preserved in their original format.
- Table 5 (View Result) has a step numbering gap: user steps go 1, 4, 5, 6 and system steps go 2, 3, 7 — this matches the source document and was preserved verbatim.
- Table 6 (View Posts) contains a typo in step 7: "Optionaly" — preserved verbatim from source.

### Unclear items
- The exception condition codes (E1, E2, E3, etc.) are not consistent across use cases — e.g., "E2" means "No Analytics Available" in one table and "Database Connection Failure" in another. The report does not provide a master exception code list.
- Section 3.4 Non-Functional Requirements lists items without IDs or priority levels.
- No priority, source, or traceability matrix linking functional requirements to use cases is provided in the report.
