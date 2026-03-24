**Sentiment Analysis using LLMs**

**(Hawa)**

IS498 Capstone Project Report

```
Prepared by:
```
```
Abdulrahman Alwabil 444101867
Omar Albassam 444101013
Abdulrahman Aldakheel 444101543
Naser Alzughaibi 444100546
```
```
Supervised by: Dr. Abdulsalam Alsunaidi
```

## Abstract

Marketing teams face challenges in efficiently analyzing large volumes of social media
data to understand brand health. Traditional sentiment analysis tools often oversimplify
complex customer opinions by reducing them to simple positive/negative classifications,
missing important contextual signals such as emotions and specific product aspects.

This report presents the design and development of Hawa, an LLM-powered sentiment
analysis system that extracts multi-dimensional insights from social media posts. The sys-
tem identifies sentiment scores, emotions, and brand-related aspects from text data, then
aggregates these results into visualizations and reports that support data-driven brand man-
agement decisions. The platform accommodates both automated social media data collec-
tion and manual dataset uploads.

A functional prototype demonstrates the feasibility of the approach, validating that LLMs
can perform multi-dimensional sentiment analysis with reasonable accuracy. This work
establishes a foundation for automated brand health monitoring that bridges the gap be-
tween high-volume social media data and actionable marketing intelligence.


## TABLE OF CONTENTS



- CHAPTER 1: INTRODUCTION
- 1.1 Background
   - 1.1.1 General Context
   - 1.1.2 Motivation
- 1.2 Problem Statement
- 1.3 Project Objectives
- 1.4 Scope of the Project
   - 1.4.1 In Scope
   - 1.4.2 Core Functions
   - 1.4.3 Out of Scope
- 1.5 Project Schedule
   - 1.5.1 Gantt Chart
   - 1.5.2 Milestones and Deliverables
- CHAPTER 2: RELATED WORK
- 2.1 Existing Sentiment Analysis Technologies & Solutions
   - 2.1.1 Lexicon-based and Rule-based Methods
   - 2.1.2 Machine-Learning Methods
   - 2.1.3 Large Language Models (LLMs) for Sentiment Analysis
   - 2.1.4 Existing Solutions
- 2.2 Technologies Considered
   - 2.2.1 Large Language Models
   - 2.2.2 Evaluation Methodology
   - 2.2.3 Production-Level LLMs
   - 2.2.4 Open Source LLMs for English
   - 2.2.5 Open Source LLMs for Arabic
   - 2.2.6 Social Media APIs
- CHAPTER 3: SYSTEM ANALYSIS
- 3.1 Development Methodology
- 3.2 Positional Users
   - 3.2.1 Marketing Team User
   - 3.2.2 System Administrator
- 3.3 Functional Requirements
- 3.4 Non-Functional Requirements
- 3.5 Use Case Model
   - 3.5.1 Use Case Diagram
   - 3.5.2 Use Case Descriptions
- 3.5 Prototyping & Feasibility Testing
- CHAPTER 4: SYSTEM DESIGN
- 4.1 System Architecture
- 4.2 System Design
   - 4.2.1 Domain Class Diagram
   - 4.2.2 Sequence Diagrams
   - 4.2.3 Activity Diagrams
   - 4.2.4 Entity-Relationship Diagram
   - 4.2.5 Relational Model
   - 4.2.6 UI Mockups
- CHAPTER 5: CONCLUSION
- 5.1 Challenges
- 5.2 Conclusion
- 5.3 Social, Ethical, Legal, Global, and Security Impact
- REFERENCES
- FIGURE 1: GANTT CHART LIST OF FIGURES
- FIGURE 2: USE CASE DIAGRAM
- FIGURE 3: PROTOTYPE FLOWCHART
- FIGURE 4: SYSTEM ARCHITECTURE DIAGRAM
- FIGURE 5: CLASS DIAGRAM
- FIGURE 6: START ANALYSIS SEQUENCE DIAGRAM
- FIGURE 7: UPLOAD CUSTOM DATASET SEQUENCE DIAGRAM
- FIGURE 8: GENERATE REPORT SEQUENCE DIAGRAM
- FIGURE 9: VIEW RESULT HISTORY SEQUENCE DIAGRAM
- FIGURE 10: VIEW RESULT SEQUENCE DIAGRAM
- FIGURE 11: VIEW POSTS SEQUENCE DIAGRAM
- FIGURE 12: VIEW STATUS INDICATOR SEQUENCE DIAGRAM
- FIGURE 13: VIEW ASPECTS SEQUENCE DIAGRAM
- FIGURE 14: REPORT INACCURATE REVIEW SEQUENCE DIAGRAM
- FIGURE 15: START ANALYSIS ACTIVITY DIAGRAM
- FIGURE 16: UPLOAD CUSTOM DATASET ACTIVITY DIAGRAM
- FIGURE 17: ENTITY-RELATIONSHIP DIAGRAM
- FIGURE 18: RELATIONAL MODEL
- FIGURE 19: MAIN DASHBOARD VIEW
- FIGURE 20: START ANALYSIS VIEW
- FIGURE 21: VIEW REPORTS HISTORY VIEW
- FIGURE 22: BRAND MANAGEMENT VIEW
- FIGURE 23: VIEW RESULT VIEW
- FIGURE 24: VIEW POSTS VIEW
- FIGURE 25: VIEW ASPECTS VIEW
- FIGURE 26: VIEW STATUS INDICATOR VIEW
- TABLE 1: PRODUCTION LLMS SENTIMENT ANALYSIS PERFORMANCE LIST OF TABLES
- TABLE 2: OPEN-SOURCE LLMS SENTIMENT ANALYSIS PERFORMANCE
- TABLE 3: OPEN SOURCE LLMS ARABIC SENTIMENT ANALYSIS PERFORMANCE
- TABLE 4: START ANALYSIS USE CASE DESCRIPTION
- TABLE 5: VIEW RESULT USE CASE DESCRIPTION
- TABLE 6: VIEW POSTS USE CASE DESCRIPTION
- TABLE 7: VIEW ASPECTS USE CASE DESCRIPTION
- TABLE 8: VIEW STATUS INDICATOR USE CASE DESCRIPTION
- TABLE 9: REPORT INACCURATE REVIEW USE CASE DESCRIPTION
- TABLE 10: VIEW ANALYTICS USE CASE DESCRIPTION
- TABLE 11: UPLOAD CUSTOM DATASET USE CASE DESCRIPTION
- TABLE 12: GENERATE REPORT USE CASE DESCRIPTION
- TABLE 13: PROTOTYPE SAMPLE RESULT ON MCDONALD'S USING (-4, 4) SCORING


```
Chapter 1: Introduction 1.4 Scope of the Project
```
## CHAPTER 1: INTRODUCTION

## 1.1 Background

### 1.1.1 General Context

Marketing teams rely heavily on understanding customer sentiment from social media platforms such as
TikTok, Reddit, X, Instagram, and YouTube. These platforms generate enormous amounts of
unstructured text that contain valuable opinions about brands.

### 1.1.2 Motivation

Manual monitoring is slow, biased, and cannot scale with the volume of daily interactions. Companies
risk missing early signals of customer dissatisfaction or emerging issues. Automated sentiment analysis
powered by LLMs promises faster insights, enabling proactive brand management.

## 1.2 Problem Statement

Marketing teams lack an efficient way to transform massive volumes of unstructured social media
interactions into actionable insights about brand health. Existing tools often oversimplify sentiment (just
positive/negative) and fail to capture nuances such as emotions, context, sarcasm, or specific brand
aspects (product quality, pricing, service). This gap prevents teams from making timely, data-driven
decisions to protect and enhance brand reputation.

## 1.3 Project Objectives

● Identify and classify the sentiment of social media posts related to a brand.
● Detect key aspects driving customer opinions (e.g., service, price, delivery).
● Aggregate results into a Brand Status indicator to support marketing teams.
● Provide a data-driven foundation for proactive decision-making in brand management.

## 1.4 Scope of the Project

### 1.4.1 In Scope

Data Sources: Publicly available social media interactions.
Languages: English text only. (Arabic, if feasible).

### 1.4.2 Core Functions

1. Sentiment classification (positive, negative, neutral) as a score.
2. Aspect-based sentiment (service, product, delivery, pricing, etc.).
3. Emotion detection (anger, joy, sadness, etc.) at post level.
4. Aggregation into a Brand Status indicator (score or label).
5. Visualization/reporting in a dashboard.

**Target Users** : Marketing teams in organizations.
**Evaluation** : Accuracy of sentiment classification against labeled data.


```
Chapter 2: Related Work 2.1 Existing Sentiment Analysis Technologies
& Solutions
```
### 1.4.3 Out of Scope

- Real-time data ingestion at production scale.
- Automatic response generation to customers.
- Deep integration with CRM/marketing platforms.
- Multilingual coverage beyond English and Arabic.

## 1.5 Project Schedule

### 1.5.1 Gantt Chart

```
Figure 1 : Gantt Chart
```
### 1.5.2 Milestones and Deliverables

```
● Requirements gathering completed.
● Data collection and preprocessing completed.
● System analysis & design completed.
● LLM analyzer prototype completed.
● Documentation and reporting completed.
```
## CHAPTER 2: RELATED WORK

## 2.1 Existing Sentiment Analysis Technologies & Solutions

Over the last years, sentiment analysis has moved from simple word lists to machine learning, deep
learning, and, more recently, large language models (LLMs). Research on sentiment analysis commonly
groups techniques into two main families, lexicon-based and traditional machine-learning, with LLM-
based methods now emerging as a third category.


```
Chapter 2: Related Work 2.1 Existing Sentiment Analysis Technologies
& Solutions
```
### 2.1.1 Lexicon-based and Rule-based Methods

Lexicon-based methods rely on predefined lists of positive and negative words (sentiment lexicons). A
simple scoring function counts or weights the sentiment-bearing words in a text to decide whether the
overall sentiment is positive, negative, or neutral.

**Characteristics of Lexicon-based methods**

- Use sentiment dictionaries and manually defined rules
- They do not need training data with labels (positive/negative), so they are easy to use when
    labeled examples are not available

**Limitations of Lexicon-based methods**

- Sensitive to domain, slang, and informal language (especially social media)
- Struggle with sarcasm, negation (“not good”), and mixed opinions
- Usually produce only a simple polarity result (positive/negative/neutral) and do not naturally
    provide aspect-level sentiment or detailed emotion categories

### 2.1.2 Machine-Learning Methods

Machine-learning methods learn from example texts that are already labeled (for example, positive or
negative) and then use the learned patterns to predict the sentiment of new texts. This family includes
both older models with simple features and newer deep learning models that learn their own text
representations. These models usually perform better than lexicon-based methods, but the main drawback
is that they need labeled training data for each domain or language.

### 2.1.3 Large Language Models (LLMs) for Sentiment Analysis

Recent work evaluates large language models as a new class of sentiment-analysis technology. Zhang et
al. (2024) conduct a large empirical study where they test several LLMs on 26 different datasets to see
how well they perform for sentiment analysis.

Their findings show that LLMs:

- Can perform sentiment-analysis tasks in **zero-shot and few-shot** settings using prompts, without
    full supervised training for each dataset
- Achieve competitive performance with smaller, task-specific models on **simpler sentiment**
    **classification tasks**
- Are able, in principle, to output richer structures (e.g., sentiment labels, emotions, and aspects) in
    a single prompted response

At the same time, Zhang et al. report that:

- LLMs still **lag behind** well-trained, smaller models on more complex, structured tasks such as
    full ABSA and some MAST tasks
- LLM performance is sensitive to **prompt design** and output formatting, especially when the
    required structure is complex

The literature positions LLM-based techniques as the latest layer in the evolution of sentiment analysis,
built on top of lexicon-based, machine-learning-based, and deep learning-based approaches. For our


```
Chapter 2: Related Work 2.2 Technologies Considered
```
project, this is relevant because we aim to use an LLM to generate multi-dimensional outputs (overall
sentiment, emotions, and brand-related aspects).

### 2.1.4 Existing Solutions

**Local Solution – Lucidya**
Lucidya is a Saudi-founded, AI-powered customer experience and social listening platform
headquartered in Riyadh and focused on organizations in Saudi Arabia. It offers a unified suite that
includes social listening, media monitoring, and analytics for marketing, with **sentiment analysis**
specifically optimized for Arabic content. Lucidya reports around 92% sentiment-analysis accuracy for
Arabic.

**International Solution – Brandwatch**
Brandwatch is a widely used social listening and analytics platform that collects data from social
networks, forums, blogs, and news sites and applies AI-based sentiment analysis. Its engine classifies
conversations by polarity (positive, neutral, negative) and can further label them with basic emotions
such as anger, disgust, fear, joy, surprise, and sadness, which are then visualized in interactive
dashboards for campaign tracking and crisis detection.

## 2.2 Technologies Considered

### 2.2.1 Large Language Models

In the context of brand sentiment analysis, LLMs offer significant advantages over traditional rule-based
or classical machine learning approaches. Traditional sentiment analysis tools typically rely on
predefined lexicons or simple classification algorithms that struggle with nuanced language, sarcasm,
context-dependent meanings. LLMs, by contrast, leverage their extensive pre-training to:

1. **Understand contextual meanings** : LLMs can distinguish between “The battery life is sick!”,
    which in slang is positive, and “I feel sick of this battery life” which is negative.
2. **Multi-dimensional analysis** : A single LLM can simultaneously extract sentiment scores,
    identify emotions, and detect aspects being discussed.
3. **Adaptation to brand-specific language** : LLMs can interpret product names and brand
    terminology without the need for explicit programming.

In our sentiment analysis system, the LLM serves as the core analytical engine. When a social media
post enters the system, it is preprocessed (cleaned) and then sent to the LLM with a structured prompt.
The prompt instructs the model to return three key outputs:

- **Sentiment Score** : Numerical value (0-5 scale) indicating sentiment polarity
- **Dominant Emotion** : The primary emotion expressed (e.g. joy, anger, frustration)
- **Aspect** : The specific brand element being discussed (e.g. product, price, delivery)


```
Chapter 2: Related Work 2.2 Technologies Considered
```
### 2.2.2 Evaluation Methodology

To assess the capabilities of available LLM technologies for sentiment analysis, we evaluated
representative models from both commercial services and open-source alternatives. This evaluation
establishes performance expectations and identifies trade-offs between the different approaches. It's
worth noting that we evaluated the LLMs by processing the entire dataset at once rather than handling
each item individually. While one-by-one processing produces more consistent outcomes, we agreed that
batch processing was preferable for this phase of the project because it introduces greater variance in the
results, making our assessment more straightforward.

**Test Dataset**
We constructed a control dataset of 50 social media posts about a major technology product (iPhone 17 ).
Each post was manually annotated for sentiment score (0-5 scale), dominant emotion, and aspect being
discussed. A smaller subset of 10 posts from this dataset was made to test local LLMs, because of issues
with local LLMs due to hardware limitations.

**Evaluation Metrics**
**_Mean Absolute Eror (MAE)_** measures the average magnitude of prediction errors:

```
𝑀𝐴𝐸=√
```
#### 1

#### 𝑛×∑

#### |𝑝𝑟𝑒𝑑𝑖𝑐𝑡𝑒𝑑−𝑎𝑐𝑡𝑢𝑎𝑙|

MAE provides an intuitive understanding of typical accuracy. For example, MAE of 0.35 indicates
predictions deviate from ground truth by an average of 0.35 points on the 0-5 sentiment scale. Lower
values indicate better performance.

**_Root Mean Square Error (RMSE)_** essentially provides similar insights to MAE, but penalizes larger
errors more heavily:

#### 𝑅𝑀𝑆𝐸=√

#### 1

#### 𝑛∑(𝑝𝑟𝑒𝑑𝑖𝑐𝑡𝑒𝑑−𝑎𝑐𝑡𝑢𝑎𝑙)

(^2)
By squaring errors before averaging, RMSE disproportionately penalizes catastrophic misclassifications.
This is particularly important for brand monitoring, where missing a viral negative post has greater
consequences than minor scoring imprecision across many routine posts.
**Testing Procedure**
All models were evaluated under identical conditions:

- Identical generic prompt
- No pre-training, nor fine-tuning
- Each model processed the whole batch at once


```
Chapter 2: Related Work 2.2 Technologies Considered
```
**Prompt Message**
You are an expert sentiment analyzer, and your job is to take a CSV file that has posts
about the topic of iPhone 17, and provide the following for each post:
* Sentiment Score from 0.0 (very negative) to 5.0 (very positive), where between 2 and 3 is
neutral.
* The dominant emotion that lays within the text. (e.g. happy, sad, angry, neutral,
optimistic, etc.)
* The aspect of which the post focused on (e.g. product, service, quality, delivery, price,
etc.)

Examples:

1. "The new iPhone 17 color is so freaking ugly, whoever thought of orange as a phone's
color should be immediately fired.":
1. Score: 0.
2. Emotion: Angry
3. Aspect: Product
2. "Although the new iPhone 17 is expensive, it provides a lot of useful features that are
exclusive to the iPhone.":
1. Score: 2.
2. Emotion: Neutral
3. Aspect: Price
3. "I just got my iPhone delivered this evening when I just ordered this morning! Bravo,
Apple!":
1. Score: 4.
2. Emotion: Happy
3. Aspect: Delivery

Respond only in CSV format that contains the post text, score, emotion and aspect as
columns.
Preserve the same order of the original file, and make sure you've analyzed exactly 50
posts.

### 2.2.3 Production-Level LLMs

We evaluated 5 commercially available LLMs:

1. **Perplexity**
2. **Grok 4.**
3. **GPT 5.**
4. **Deepseek-R**
5. **Gemini 3 Pro**


```
Chapter 2: Related Work 2.2 Technologies Considered
```
**Performance Results
Model Sentiment MAE Sentiment RMSE Sentiment Average**
Control - - 2.
Perplexity 0.554 0.446 3.
Grok 4.1 0.562 0.661 3.
GPT 5.1 0.804 0.986 2.
Deepseek-R1 0.520 0.645 3.
**Gemini 3 Pro 0.318 0.445 2.**
Table 1 : Production LLMs Sentiment Analysis Performance

The table reveals that Gemini 3 Pro achieved the best performance with the lowest MAE of 0.318 and
RMSE of 0.445 when tested against the control dataset. Perplexity and GPT 5.1 also demonstrated prom-
ising results, which suggests that further refinement of their prompts could yield more consistent and
reliable outcomes.

### 2.2.4 Open Source LLMs for English

We evaluated several open-source LLM alternatives:

1. **Llama 3.2 3B Instruct** - Meta's multilingual model optimized for dialogue use cases, including
    agentic retrieval and summarization tasks. Officially supports multiple languages. Features 128K
    context length support.
2. **Qwen 3-VL 4B** - Alibaba's vision-language model from the Qwen series, designed to process
    both text and images with strong reasoning capabilities across multiple languages.
3. **Jais 7B** - Developed by Inception and Cerebras Systems, this model is part of the Jais family of
    bilingual English-Arabic large language models. Jais-adapted models are pre-trained adaptively
    from Llama-2 and optimized to excel in Arabic (MSA) while maintaining strong English
    capabilities.
4. **ALLaM 7B Instruct** - Developed by the National Center for Artificial Intelligence (NCAI) at
    the Saudi Data and AI Authority (SDAIA), ALLaM is a series of powerful language models
    designed to advance Arabic Language Technology.

**Performance Resutls
Model Sentiment MAE Sentiment RMSE Sentiment Average**
Control - - 3.
Llama 3.2 0. 800 1.158 **3.**
Qwen 3-VL **0.420** 0.694 3.
Jais 1.060 1.391 4.
ALLaM 0.5 40 **0.656** 3.

## TABLE 2: OPEN-SOURCE LLMS SENTIMENT ANALYSIS PERFORMANCE

Due to the batch-processing approach and a reduced sample size compared to what was provided to
production-level LLMs, this table displays somewhat greater deviation from the control dataset than Ta-
ble 1, with notably higher variance across each metric. No definitive leader emerges, highlighting the
challenges associated with open-source LLMs.


```
Chapter 2: Related Work 2.2 Technologies Considered
```
### 2.2.5 Open Source LLMs for Arabic

We conducted exploratory testing to assess the feasibility of future Arabic language support. This inves-
tigation was motivated by the unique technical challenges Arabic presents: dialectical variations, and the
prevalence of informal social media language that differs substantially from Modern Standard Arabic
(MSA). (MSA is the formal, standardized form of Arabic, evolving from Classical Arabic).

Understanding Arabic sentiment analysis capabilities would inform future roadmap decisions and help
estimate the effort required to expand the system's linguistic coverage. This exploratory phase focused
on establishing baseline performance metrics and identifying which model architectures show promise
for Arabic text analysis.

**Test Dataset**
We constructed a control dataset of 10 Arabic social media posts about Almarai dairy company. Each
post was manually annotated for sentiment score (0-5 scale), dominant emotion, and aspect being dis-
cussed. The posts were in Saudi dialect, and were found on X.

**Performance Results**
We evaluated the same open source models in subsection **2. 2. 4** :

```
Model Sentiment MAE Sentiment RMSE Sentiment Average
Control - - 3. 81
Llama 3.2 1. 54 1. 708 3. 07
Qwen 3-VL 1. 23 1. 650 3.2 8
Jais 0. 61 0. 753 3. 48
ALLaM 0. 89 1. 182 3. 88
```
## TABLE 3: OPEN SOURCE LLMS ARABIC SENTIMENT ANALYSIS PERFORMANCE

The table reveals that Jais achieved the best performance with the lowest MAE of 0.61 and RMSE of
0.75 3 when tested against the control dataset. ALLaM also demonstrated promising results with an MAE
of 0.89, which suggests that Arabic-specialized models significantly outperform general multilingual
alternatives. These results indicate that Arabic sentiment analysis – while technically feasible – still lacks
behind English sentiment analysis.

### 2.2.6 Social Media APIs

Data collection from social media platforms presented significant technical and practical challenges. The
ability to gather relevant posts at scale directly impacts the system’s effectiveness.

**Web Scraping Tools**
Initial exploration focused on web scraping tools as a cost-effective alternative to official APIs. Several
Python-based scraping libraries were evaluated, including _snscrape_ , which had been widely used for
collecting social media data from platforms like X (formerly Twitter). However, recent policy changes
on X have rendered traditional scraping approaches useless.

X implemented stricter anti-scraping measures and rate limiting in 2023–2024, requiring authentication
for even basic content access. These changes broke compatibility with most third-party scraping tools.
Specifically, _snscrape_ —which had previously enabled unauthenticated data collection—ceased


```
Chapter 3: System Analysis 3.1 Development Methodology
```
functioning after X’s platform updates. Attempts to work around these restrictions proved unsuccessful,
as X continuously updates its defenses against automated data collection.

**Official X API**
The official X API represents the authorized method for accessing platform data, but pricing presents a
significant barrier for academic and small-scale projects. The current API tier structure includes:

- **Free Tier** : 100 posts/month
- **Basic Tier** : 10,000 posts/month for $200/month

For a sentiment analysis system requiring hundreds or thousands of posts to generate statistically
meaningful insights, the free tier’s 1 00 - post monthly limit is insufficient. A single brand analysis session
could easily exceed this quota.

**Reddit API (PRAW)**
Given the limitations with X, the project moved to Reddit as the primary data source using the Python
Reddit API Wrapper (PRAW). PRAW offers free access with much more generous rate limits than X
API.

## CHAPTER 3: SYSTEM ANALYSIS

## 3.1 Development Methodology

In software engineering theory, process models are often presented as either plan-driven (such as the
waterfall model) or agile. Plan-driven approaches assume that requirements, design, and plans can be
defined in detail early and then followed step by step, while agile approaches organize the work into
short iterations that deliver working software and expect requirements to change over time. Sommerville
notes that real projects rarely follow a pure form of either model; instead, industry tends to mix agile and
plan-based practices and combine the strengths of both. We will follow this view: we use some upfront
planning and documentation, but our methodology is clearly biased towards an agile, iteration-based way
of working.

This agile orientation fits our system and constraints. The project depends on external social-media data
sources and LLMs whose behavior we cannot fully fix at the start. On the data side, platforms such as X
have tightened access, rate limits, and pricing, which forced us to move from scraping tools to the Reddit
API (PRAW) as a more realistic source. On the model side, our evaluation shows that different LLMs
(commercial and open source) offer different trade-offs in accuracy, Arabic support, context handling,
and cost, and many state-of-the-art models are either too expensive or too heavy for our available
hardware. These factors mean that both data sources and model choices must be adjusted over time based
on experiments rather than decided once. An agile process that uses sprints, a prioritized backlog, and
regular re-planning allows us to respond to these changes instead of treating them as exceptions.


```
Chapter 3: System Analysis 3.3 Functional Requirements
```
## 3.2 Positional Users

### 3.2.1 Marketing Team User

**Role:** Interacts directly with the system to generate sentiment analysis reports and interpret customer
feedback insights.
**Responsibilities:**

- Initiates brand sentiment analysis by selecting sources or uploading datasets
- Reviews sentiment results, emotional distributions, aspect-based insights, and summary
    indicators
- Provides feedback on accuracy of analysis
- Uses insights to support decision-making and marketing strategies

### 3.2.2 System Administrator

**Role:** Manages user accounts, system configurations, and views analytical data.
**Responsibilities:**

- Creates and approves user accounts
- Manages system permissions and user access levels
- Monitors system status and ensures smooth operation

## 3.3 Functional Requirements

**Data Handling**
● The system shall collect posts from a social media platform.
● The system shall allow uploading of custom datasets in CSV format.
● The system shall detect language (Arabic/English) and preprocess text for analysis.

**Sentiment & Aspect Analysis**
● The system shall assign a score to represent sentiment (negative, neutral, positive).
● The system shall provide a confidence score for each classification.
● The system shall detect main aspects such as product, service, delivery, and pricing.
● The system shall detect basic emotions including joy, anger, and sadness.

**Dashboard & Reporting**
● The system shall provide a dashboard showing sentiment trends, aspect analysis, and emotion
distribution.
● The system shall allow filtering by date, sentiment type, aspect, and language.
● The system shall allow exporting reports in CSV format.

**User Roles & Feedback**
● The system shall support role-based access (Admin, Marketing Team).
● The system shall allow users to provide feedback on misclassified posts for model improvement.


```
Chapter 3: System Analysis 3.4 Non-Functional Requirements
```
## 3.4 Non-Functional Requirements

**Performance**
● The system shall return dashboard results within 2 seconds for standard queries.

**Usability**
● The system shall provide a dashboard that enables non-technical users to access key sentiment
metrics within 3 clicks from the home screen.
● The system shall support responsive design for desktop and mobile access.

**Accuracy & Quality**
● The system shall maintain consistent sentiment analysis accuracy (within 5% variance) for
analyzed posts

**Security**
● The system shall enforce secure authentication and role-based access control

**Reliability & Availability**
● The system shall handle errors gracefully and provide clear error messages

**Maintainability & Scalability**
● The system architecture shall separate the LLM analyzer, data processing, and presentation layers
through well-defined APIs
● The system shall support scaling to additional data sources in future phases


```
Chapter 3: System Analysis 3.5 Use Case Model
```
## 3.5 Use Case Model

### 3.5.1 Use Case Diagram

## FIGURE 2: USE CASE DIAGRAM

The system supports two main actor roles: Marketing Team Users, who perform sentiment analysis and
review results, and Admins, who manage user accounts and monitor system analytics.

Marketing Team Users can initiate sentiment analysis through two pathways: collecting data directly
from social media platforms via Start Analysis or uploading custom datasets through Upload Custom
Dataset. Once analysis is complete, users access comprehensive results through View Result, which
serves as the central hub for detailed examination. From this hub, users can navigate to detailed views
including View Aspects, View Status Indicator, and View Posts. Additionally, users can view historical
reports through View Result History, exporting data via Generate Report, and reporting
misclassifications through Report Inaccurate Review, which extends from View Posts.

Admin users have access to system management functions including Create User Account for managing
system access, View Analytics for monitoring overall system usage and LLM performance across the
platform, and View Reported Reviews to examine misclassifications.


```
Chapter 3: System Analysis 3.5 Use Case Model
```
### 3.5.2 Use Case Descriptions

```
Use Case Name Start Analysis
Scenario Marketing team user starts an analysis
Triggering Event Marketing team user click the “Start Analysis” button on the dashboard
```
```
Brief Description
```
```
This use case allows marketing team employees to initiate
automated sentiment analysis on social media posts related to
their brand. The system collects posts from specified sources,
analyzes them using LLM-based processing to extract
sentiment, emotions, and aspects, then aggregates the results
into a comprehensive brand status report.
Actors Marketing Team User
Related Use Cases (if
any)
```
```
None
```
```
Preconditions • •^ System must have connectivity to social media APILLM Analyzer service must be operational
```
```
Postconditions
```
- Analysis report is generated and stored in the database
- Dashboard displays “Analysis Complete” status message
- “View Results” button is enabled to the user

```
Flow of Activities
```
```
Marketing Team User System
```
1. Clicks “Start Analysis”
    button
2. Fills in config form
3. Submits analysis request
4. Wait for analysis to
    complete
5. View “Analysis Complete”
    message
6. Clicks “View Results” to
    access report

```
1.1 Displays analysis config
form to user
2.1 Validate input parameters
3.1 Retrieve brand details and
keywords
3.2 Display “Analysis Started”
message
4.1 Collect posts from social
media
4.2 Send posts to LLM
analyzer
4.3 Receive results and
generate report
5.1 Display “Analysis
Complete” message
6.1 Retrieve and display
complete report
```
```
Exception Conditions
```
```
E2.1 Invalid Input
E4.1 Social media API failure
E4.2 LLM analyzer unavailable
```
## TABLE 4: START ANALYSIS USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name View Result
```
```
Scenario
```
```
Marketing Team User views the results of a sentiment
analysis report (either newly generated or previously
saved)
Triggering Event User clicks on a completed report from View Report History or dashboard
```
```
Brief Description
```
```
This use case allows the Marketing Team User to view an
overview and summary of a completed sentiment analysis
report. The overview displays aggregated sentiment
scores, emotion distribution, aspect breakdown, and
overall confidence levels for a brand's analyzed social
media posts. From this view, users can navigate to more
detailed views of aspects, status indicators, and individual
posts.
Actors Marketing Team User
Related Use Cases (if
any)
```
```
View Aspects, View Status Indicator, View Posts, View
Report History, Generate Report
```
```
Preconditions
```
- **The Marketing Team User is authenticated and logged**
    **in.**
- **At least one analysis report must exist for the selected**
    **brand**
- **The report status must be "finished" (not "processing"**
    **or "error")**

```
Postconditions
```
- **User successfully views the analysis report overview**
    **and summary**
- **User can navigate to related detailed views (View**
    **Aspects, View Status Indicator, View Posts)**

```
Flow of Activities
```
```
Marketing Team User System
```
**1. Clicks on a completed**
    **report from the report**
    **list or dashboard
4. Reviews displayed**
    **report summary
5. Optionally navigates to**
    **View Aspects, View**
    **Status Indicator, or View**
    **Posts for detailed**
    **analysis
6. Optionally, clicks on**
    **Generate Report**
       **2. Retrieves report data from**
          **the database**
       **3. Displays the report**
          **overview**
       **7. Handled by Genrate**
          **Report use case**

**Exception Conditions**

```
E2 No Analytics Available
E3 Analysis Failed
E4 Analysis Still Processing
```
## TABLE 5: VIEW RESULT USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name View Posts
Scenario Marketing Team User views individual analyzed posts from a sentiment analysis report
```
```
Triggering Event User clicks on the posts card/section within the View Result dashboard
```
```
Brief Description
```
**This use case allows the Marketing Team User to view
detailed information about individual social media posts
that were analyzed in a report. Posts are displayed in a
paginated table format showing post text, sentiment score,
dominant emotion, detected aspect, and confidence level.
Users can filter and sort posts by various criteria including
sentiment range, emotion type, aspect type, confidence
level, and date range.
Actors Marketing Team User
Related Use Cases (if any) View Result, Report Inaccurate Analysis**

```
Preconditions
```
- **The Marketing Team User has accessed View Result**
    **page**
- **Post analysis is complete with sentiment scores,**
    **emotions, aspects, and confidence**

```
Postconditions
```
- **User successfully views the list of analyzed posts**
- **User can filter and sort posts by multiple criteria**
- **User returns to the View Result dashboard**
- **User can report inaccurate analysis for individual posts**

```
Flow of Activities
```
```
Marketing Team User System
```
**1. Clicks on the posts card
4. Reviews displayed**
    **posts
5. Optionally apply filters**
    **and sorting
7. Optionaly report**
    **inaccurate analysis**
       **2. Retrieves post & analysis**
          **data from database**
       **3. Displays posts in**
          **paginated table format:**
          **Post text, Sentiment**
          **score, Dominant emotion.**
          **Detected aspect.**
          **Confidence level.**
       **6. Updates view to reflect**
          **filters and sorting**
       **8. Handled by Report**
          **Inaccurate Analysis**

```
Exception Conditions E2 Database Connection FailureE5 No Posts Match Filter Criteria^
```
## TABLE 6: VIEW POSTS USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name View Aspects
Scenario Team User views the aspect breakdown of a sentiment analysis report
```
```
Triggering Event User clicks on the aspects card/section within the View Result dashboard
```
```
Brief Description
```
```
This use case allows the Marketing Team User to view a
detailed breakdown of aspects detected in analyzed social
media posts. The aspects view displays aspect types ranked by
frequency (number of posts mentioning each aspect), along with
corresponding sentiment scores, emotion distribution per
aspect, and post counts. This enables marketing teams to
identify which specific areas (product, service, pricing, delivery,
customer service, etc.) are driving customer sentiment and
where attention is needed.
Actors Marketing Team User
Related Use Cases (if
any)
```
```
View Result
```
```
Preconditions •^ The Marketing Team User has accessed View Result page^
```
- The report contains complete analyzed posts with aspects

```
Postconditions
```
- User successfully views the aspect analysis breakdown
- User can filter aspects by aspect type
- User returns to the View Result overview

```
Flow of Activities
```
```
Marketing Team User System
```
1. Clicks on the aspects
    card
4. Reviews the displayed
    aspect analysis and
    statistics
5. Returns to View Result
    overview
       2. Retrieves aspect data from
          database
       3. Displays the aspect analysis
          data:
             a. Aspect name
             b. Posts included in each
                aspect
             c. Average score per
                aspect
             d. Emotion distribution

**Exception Conditions** E1 No Aspects Detected in AnalysisE2 Database Connection Failure

## TABLE 7: VIEW ASPECTS USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name View Status Indicator
Scenario Marketing Team User views the overall brand status indicator
Triggering Event User clicks on the status indicator card/section within the View Result dashboard
```
```
Brief Description
```
```
This use case allows the Marketing Team User to view the
aggregated brand status indicator, which provides a
comprehensive overview of the overall sentiment health. The
status indicator displays a color-coded sentiment score, emotion
analytics including the dominant emotion and top emotions
breakdown, confidence metrics, emotion diversity, and an LLM-
generated interpretive comment summarizing the analysis
findings.
Actors Marketing Team User
Related Use Cases (if
any)
```
```
View Result
```
```
Preconditions
```
- The Marketing Team User has accessed View Result page
- Aggregated calculations for the report are complete
- The report contains analyzed posts
**Postconditions** •^ User successfully views the brand status indicator metrics^
- User returns to the View Result overview

```
Flow of Activities
```
```
Marketing Team User System
```
1. Clicks on the status
    indicator card
4. Reviews the displayed
    status metrics and
    insights
5. Returns to View Result
    overview
       2. Retrieves report score data
          from database
       3. Displays the status indicator
          data:
             a. Color-coded status
             b. Sentiment breakdown
             c. Dominant emotion
             d. Top 3 emotions
             e. Diversity score
             f. Confidence level
             g. Summary

**Exception Conditions** E2 Database Connection Failure

## TABLE 8: VIEW STATUS INDICATOR USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name Report Inaccurate Review
```
```
Scenario
```
```
After viewing the analyzed posts inside the View
Posts use case, the user may notice that a specific
post’s sentiment, emotion, or aspect classification is
incorrect. The user clicks the “Inaccurate Analysis”
button and submits a brief explanation. The system
records this feedback for model improvement.
Triggering Event The user clicks analyzed posts. “Inaccurate Analysis” after viewing
```
```
Brief Description
```
This use case allows the marketing team user to
report that the system misinterpreted a specific post.
When triggered, the system displays a small feedback
input box. The user types a brief description and
submits it. The feedback entry is stored in the system
and linked to the corresponding analysis record for
future review and model refinement.
**Actors** Marketing Team User
**Related Use Cases (if any)** View Posts

```
Preconditions
```
- The user is logged in.
- The user is currently inside the **View Posts** use
    case.
- The analysis has been completed and posts are
    available to review.

```
Postconditions
```
- The feedback entry is saved in the system.
- The feedback is linked to the correct userId,
    analysisId, and post.
- A confirmation message is returned to the user.

```
Flow of Activities
```
- **Actor’s Actions**
    The user clicks “Inaccurate Analysis” after viewing
    a post.
    The user enters a brief explanation of the issue.
    The user clicks “Send Feedback.”
- **System’s Responses**
    The system displays a feedback text field.
    The system receives the feedback input.
    The system saves the feedback (userId,
    analysisId, brief) through the feedback service
    and repository, then returns a success message.
-

```
Exception Conditions
```
- User submits empty feedback. System displays:
    “Please enter a brief explanation.”
- Feedback storage fails. system displays an error:
    “Could not submit feedback. Try again later.”

## TABLE 9: REPORT INACCURATE REVIEW USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name View Analytics
Scenario The Admin accesses the analytics dashboard to monitor system usage and brand analysis activity across the system.
```
```
Triggering Event The Admin navigates to the "View Analytics" page from the admin dashboard.
```
```
Brief Description
```
```
This use case allows the Admin to view comprehensive
analytics about system usage and brand analysis activity. The
analytics provide insights into LLM utilization, post analysis
trends, brand performance metrics, and other key indicators.
The Admin can apply various filters to refine the view and focus
on specific time periods, companies, brands, or other criteria.
Actors Admin
Related Use Cases (if
any)
```
```
None
```
**Preconditions** (^) • The Admin is authenticated and logged into the system.
**Postconditions**

- The system displays the latest analytical results retrieved
    from the database.
- The analytics dashboard remains accessible for further
    exploration and filtering.

```
Flow of Activities
```
```
Admin System
```
1. Navigates to "View
    Analytics"
4. Reviews displayed
    analytics
5. Optionally, applies filter
    criteria (date range,
    brands, etc.)
8. Reviewes filtered
    analytics
       2. Retrieves all analytics from
          the database
       3. Populates analytics
          dashboard
       6. Applies filter function on the
          data
       7. Displays criteria-met data

**Exception Conditions**

```
E2 No Analytics Available
E2 Database Connection Failure
E5 Invalid Filter Criteria
```
## TABLE 10: VIEW ANALYTICS USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name Upload Custom Dataset
```
```
Scenario
```
```
After starting a new analysis, the marketing team user
may choose to upload a custom dataset instead of
using the system’s default data source. This dataset is
then used as the input for sentiment analysis.
```
```
Triggering Event
```
```
The user selects “Start Analysis” , and within the
configuration options, clicks “Upload Custom
Dataset”.
```
```
Brief Description
```
his use case allows the marketing team user to upload
a CSV or XLSX file containing posts, comments, or
text data to be analyzed. The system validates the file
type and size, uploads it, stores it, and makes it
available for processing. The custom dataset replaces
the standard social media data source for the analysis
run.
**Actors** Marketing Team User
**Related Use Cases (if any)** Start Analysis

```
Preconditions
```
- The user is logged in and authorized.
- The user has already initiated the **Start**
    **Analysis** use case.
- The file is available on the user’s device.

```
Postconditions
```
- The uploaded dataset is stored in the system.
- The dataset is linked to the analysis request.
- The system is ready to run sentiment analysis
    using the custom uploaded data.

```
Flow of Activities
```
```
Actor’s Actions:
```
- The user starts a new analysis.
- The user selects “Upload Custom Dataset”.
- The user chooses a CSV/XLSX file and submits
    it.
    **System’s Responses:**
- The system displays analysis options, including
    dataset upload.
- The system opens a file upload window or
    prompt.
- The system validates the file type and size,
    uploads it, stores it, and confirms successful
    upload.
-

```
Exception Conditions
```
- **Invalid file format. System displays:**
    **“Unsupported file type. Please upload CSV or**
    **XLSX.”**
- **File too large. System displays: “File exceeds**
    **size limit.”**

## TABLE 11: UPLOAD CUSTOM DATASET USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Use Case Model
```
```
Use Case Name Generate Report
```
```
Scenario
```
```
Marketing Team User generates a downloadable
sentiment analysis report containing posts and their
analysis results in CSV format.
Triggering Event Marketing Team User clicks the "Generate Report" button in the dashboard interface.
```
```
Brief Description
```
This use case enables Marketing Team Users to
generate comprehensive reports containing sentiment
analysis results for social media posts. The system
retrieves analyzed posts from the database, including
sentiment scores, aspect analysis, and emotion
detection, then compiles them into a structured CSV file
format. The generated report provides a downloadable
file that can be used for offline analysis, presentations to
stakeholders, or integration with other business
intelligence tools.
**Actors** Marketing Team User
**Related Use Cases (if any)** View Result

```
Preconditions
```
- At least one analysis must have been completed
- The database must contain post data with analysis
    report

```
Postconditions
```
- CSV file containing posts and their sentiment analysis
    is exported successfully
- Download URL for the CSV file is created and re-
    turned to the user

```
Flow of Activities
```
```
Marketing Team User System
```
1. Clicks “Generate Re-
    port” button
6. Receive download URL
    2. Fetches posts and re-
       views from database
    3. Creates and formats a
       CSV file with report data
    4. Create a download URL
    5. Return URL to user

```
Exception Conditions
```
```
E1 Invalid Report ID
E 2 Database Connection Failure
E3 Empty Dataset
E4 CSV Service Failure
E5 Insufficient Permissions
```
## TABLE 12: GENERATE REPORT USE CASE DESCRIPTION


```
Chapter 3: System Analysis 3.5 Prototyping & Feasibility Testing
```
## 3.5 Prototyping & Feasibility Testing

To validate the feasibility of the proposed system, a functional prototype was developed and tested. The
prototype implements a complete end-to-end pipeline that collects posts from Reddit, performs data
cleaning and preprocessing, and analyzes posts individually using a prompted Llama 3.2-3B model.

Despite the model's relatively small size of 3 billion parameters, the prototype demonstrates promising
results in sentiment classification, emotion detection, and aspect extraction.

The complete prototype implementation, including source code, analysis results, and documentation, is
publicly available at a GitHub repository here.

## FIGURE 3: PROTOTYPE FLOWCHART

```
Text Score Emotion Aspect Confidence
Its very salty. I
couldn’t enjoy it -^2 sad^ product^ 0.8^
The fries!!
Nothing like fresh
fries from
McDonlad’s.
```
```
3.0 happy product 0.8
```
```
People still eat at
McDonald’s...? - 3.732^ angry^ product^ 1.0^
```
## TABLE 13: PROTOTYPE SAMPLE RESULT ON MCDONALD'S USING (-4, 4) SCORING


```
Chapter 4: System Design 4.2 System Design
```
## CHAPTER 4: SYSTEM DESIGN

## 4.1 System Architecture

The system consists of a Frontend, Backend Application, Database, Job Queue, LLM service, and Post
provider API (e.g. Reddit). These components communicate through REST APIs. Backend retrieves
posts from the post provider API, stores data in the database, and manages processing tasks (e.g. Request
analysis) through an internal job queue. The LLM service reads jobs from this queue and processes them.

## FIGURE 4: SYSTEM ARCHITECTURE DIAGRAM

## 4.2 System Design

### 4.2.1 Domain Class Diagram

## FIGURE 5: CLASS DIAGRAM


```
Chapter 4: System Design 4.2 System Design
```
### 4.2.2 Sequence Diagrams

This subsection presents the key workflows of the sentiment analysis system through detailed sequence
diagrams. Each diagram illustrates the interactions between system components, from user interface to
controllers, services, and data repositories, showing how data flows through the system to deliver
actionable insights. These workflows cover both user initiated actions and automated processes that
power the system's sentiment analysis.

**Sentiment Analysis Workflow**

## FIGURE 6: START ANALYSIS SEQUENCE DIAGRAM

The Sentiment Analysis workflow demonstrates how the system processes social media data. After the
user submits analysis parameters, the system validates inputs, retrieves relevant posts from social media
platforms, and processes them through the LLM analyzer to extract sentiment scores, emotions, and
aspects.


```
Chapter 4: System Design 4.2 System Design
```
**Uploading a Custom Dataset Workflow**

## FIGURE 7: UPLOAD CUSTOM DATASET SEQUENCE DIAGRAM

The Upload Custom Dataset workflow provides flexibility for teams to analyze historical data or
proprietary sources beyond social media feeds. The system validates uploaded files (CSV/XLSX format),
checks file integrity and structure, then stores the dataset for processing.

**Generating Report Workflow**

## FIGURE 8: GENERATE REPORT SEQUENCE DIAGRAM

The Generate Report workflow transforms analyzed sentiment data into exportable, presentation-ready
reports. The system aggregates post-level sentiment scores and analysis results, builds visualizations
showing trends and distributions, and compiles everything into a downloadable CSV file.


```
Chapter 4: System Design 4.2 System Design
```
**Viewing Previous Reports Workflow**

## FIGURE 9: VIEW RESULT HISTORY SEQUENCE DIAGRAM

The View Report History workflow enables users to access and review past analysis reports. Users can
filter reports by brand, date range, or other criteria, and the system retrieves matching reports from the
database.


```
Chapter 4: System Design 4.2 System Design
```
**Viewing Result Report of an Analysis Workflow**

## FIGURE 10: VIEW RESULT SEQUENCE DIAGRAM

The View Result workflow enables users to access completed sentiment analysis reports. When a user
clicks on a report, the system validates the report ID, retrieves report and analysis data from the database,
calculates aggregate scores, and displays a comprehensive overview including sentiment scores, emotion
distribution, aspect breakdown, confidence levels, and summary statistics. Users can optionally navigate
to detailed views (Aspects, Status Indicator, Posts) or generate a new report. Optional navigations are
discussed next.


```
Chapter 4: System Design 4.2 System Design
```
**Viewing Analyzed Posts Workflow**

## FIGURE 11: VIEW POSTS SEQUENCE DIAGRAM

The View Posts workflow allows marketing team users to examine individual analyzed posts in detail.
After clicking to view analyzed posts from a report, the system retrieves both the post data and associated
analysis results from the database.

**Viewing Status Indicator of an Analysis Workflow**

## FIGURE 12: VIEW STATUS INDICATOR SEQUENCE DIAGRAM

The View Status Indicator workflow provides an aggregated overview of brand sentiment health. When
the user clicks on the status indicator card, the system validates the report ID, retrieves all associated
analysis data, and calculates key metrics: _aggregate sentiment score_ , _emotion distribution_ , _dominant
emotion_ , _top 3 emotions_ , and _confidence level_. The dashboard then displays these computed metrics.


```
Chapter 4: System Design 4.2 System Design
```
**Viewing Aspects of an Analysis Workflow**

## FIGURE 13: VIEW ASPECTS SEQUENCE DIAGRAM

When the user clicks on the aspects card, the system validates the report ID, retrieves aspect analysis
data, groups posts by aspect type, and calculates statistics (sentiment scores, emotion distribution, post
counts) for each aspect.

**Reporting an Inaccurate Review Workflow**

## FIGURE 14: REPORT INACCURATE REVIEW SEQUENCE DIAGRAM

When a user identifies incorrect sentiment analysis on a post, they click "inaccurate analysis," enter a
feedback brief explaining the issue, and submit. The system records the feedback (userId, analysisId,
brief) in the feedbackRepository for future model improvement and returns confirmation to the user.


```
Chapter 4: System Design 4.2 System Design
```
### 4.2.3 Activity Diagrams

This subsection presents two workflows that represent the system's core data ingestion
mechanisms: **Start Analysis** and **Upload Custom Dataset**. These workflows were selected because they
represent the two primary entry points for data into the system, one handling social media data collection
and analysis, and the other enabling users to analyze manually – or by other means – collected datasets.

**Sentiment Analysis Workflow**

## FIGURE 15: START ANALYSIS ACTIVITY DIAGRAM


```
Chapter 4: System Design 4.2 System Design
```
**Uploading a Custom Dataset Workflow**

## FIGURE 16: UPLOAD CUSTOM DATASET ACTIVITY DIAGRAM


```
Chapter 4: System Design 4.2 System Design
```
### 4.2.4 Entity-Relationship Diagram

## FIGURE 17: ENTITY-RELATIONSHIP DIAGRAM


```
Chapter 4: System Design 4.2 System Design
```
### 4.2.5 Relational Model

## FIGURE 18: RELATIONAL MODEL


```
Chapter 4: System Design 4.2 System Design
```
### 4.2.6 UI Mockups

## FIGURE 19: MAIN DASHBOARD VIEW

## FIGURE 20: START ANALYSIS VIEW


Chapter 4: System Design 4.2 System Design

## FIGURE 21: VIEW REPORTS HISTORY VIEW

## FIGURE 22: BRAND MANAGEMENT VIEW


Chapter 4: System Design 4.2 System Design

## FIGURE 23: VIEW RESULT VIEW

## FIGURE 24: VIEW POSTS VIEW


Chapter 4: System Design 4.2 System Design

## FIGURE 25: VIEW ASPECTS VIEW

## FIGURE 26: VIEW STATUS INDICATOR VIEW


```
Chapter 5: Conclusion 5.2 Conclusion
```
## CHAPTER 5: CONCLUSION

## 5.1 Challenges

**1. Arabic support is harder than expected:**
We initially planned to support both English and Arabic, but Arabic turned into a major challenge. Many
general-purpose LLMs still perform noticeably worse on Arabic than on English, especially for
dialectical and informal social-media text. On top of that, there is no widely accessible social-media data
source that both has a large Arabic text audience and provides an affordable, convenient API for bulk
analysis (see Challenge 2). As a result, we treated Arabic support as a stretch goal instead of a guaranteed,
production-quality feature in this phase.
**2. Social media data access for Arabic (X is out of reach):**
The most important Arabic text-based social media platform for public discussions is X (Twitter).
However, X’s current API pricing, strict rate limits, and anti-scraping measures make it effectively out
of reach. Free or low-cost options do not provide enough volume to build a realistic dataset, and unofficial
scrapers are unreliable or blocked. Because of this, we had to abandon X as a data source, even though
it is central for Arabic conversations, and instead rely on Reddit (which is more English-focused) and
manual CSV/XLSX uploads. This directly limits how representative our analysis can be for Arabic-
speaking users and brands.
**3. Hardware limitations for local LLMs:**
Our available hardware could only handle relatively small open-source models (around 3B–7B
parameters) with acceptable latency and memory usage. Larger models either could not run at all or
required heavy quantization that hurt quality. Practically, this forced us to use a smaller local model for
the prototype and to evaluate only a small subset of posts for some open-source models. As a result, our
comparison between local and cloud LLMs is constrained and does not fully show what might be
achievable with stronger hardware.

## 5.2 Conclusion

The project delivers an LLM-based sentiment analysis prototype (Hawa) that processes social media data
and uploaded datasets to extract multiple dimensions from each post: a sentiment score, a dominant
emotion, and a brand-related aspect. These results are aggregated into dashboards and reports that
summarize brand status over time, including aspect and emotion breakdowns. The implemented system
covers the full pipeline from data ingestion (Reddit and CSV/XLSX) through LLM-based analysis to
visualization in a web-based interface. Experiments on a small labeled dataset show that LLMs can
perform multi-dimensional sentiment analysis with reasonable accuracy on English social media posts
under careful prompt design and structured output formats. Some production LLMs achieved relatively
low error on sentiment scores, indicating that they are viable engines for this type of analysis in
constrained settings. In parallel, the architecture separates the LLM service, job queue, backend, and
frontend, making it possible to swap models or extend data sources without redesigning the entire system.
However, several important limitations remain. Most importantly, Arabic support proved significantly
harder than English. General-purpose LLMs often perform worse on Arabic—especially informal,
dialect-rich social-media text—while the main Arabic text platform with a large audience (X/Twitter) is
currently inaccessible for realistic bulk analysis due to API limits, pricing, and anti-scraping measures.
As a result, the current system focuses on English first, and Arabic is treated as a stretch goal rather than


```
Chapter 5: Conclusion 5.3 Social, Ethical, Legal, Global, and Security
Impact
```
a fully delivered feature in this phase. In addition, hardware constraints restricted experiments with larger
open-source models, and the evaluation dataset is too small to claim strong, general conclusions about
performance in real-world conditions.

These limitations shape the next steps for future work:

**1. Arabic-focused evaluation and support**
Build a dedicated evaluation pipeline for Arabic using realistic regional data, and compare multiple
Arabic-capable LLMs and specialized sentiment models. Based on these experiments, select or combine
models that provide acceptable accuracy and stability for Arabic posts, or adopt a hybrid architecture
where English and Arabic use different backends.
**2. Experiments with larger cloud-hosted LLMs (vLLM setups)**
Experiment with larger models hosted on the cloud (for example, via vLLM-based deployments or
managed LLM providers) to see whether they can deliver better sentiment, emotion, and aspect
predictions, especially for Arabic. These experiments should consider not only accuracy but also latency
and cost, to understand what is realistically achievable if Hawa is scaled beyond a purely local setup.

## 5.3 Social, Ethical, Legal, Global, and Security Impact

**1. Social Impact:**
The system helps organizations turn large volumes of unstructured text into clearer signals about
customer satisfaction, complaints, and recurring issues. This can support faster reactions to problems,
more informed decisions, and more customer focused services.
At the same time, automated analysis can misread context, sarcasm, or sensitive topics. If teams trust the
system without human review, they may misinterpret what users mean or overlook important individual
feedback. The social impact therefore depends on using Hawa as support for human decision making,
not as a full replacement for careful reading and direct communication with customers.
**2. Ethical Impact**
The main ethical concerns in this project are privacy and potential misuse. The system is designed for
aggregate analysis at the level of brands and topics, using public data or datasets provided by
organizations. It is not intended to profile or track specific individuals or to monitor them in a way that
feels intrusive.
Hawa should be used to better understand customer needs and improve services, not to design
manipulative campaigns or to silence criticism based only on sentiment scores produced by the model.
For this reason, Hawa is positioned as a decision support tool, and the final responsibility for interpreting
and acting on the results remains with human users.
**3. Legal Impact**
From a legal perspective, the system must respect data protection rules and the terms of use of each
platform or data source. Public posts can still contain personal data, so the system should collect only
what is necessary for analysis, avoid storing sensitive information without need, and apply reasonable
retention and deletion practices, especially for uploaded datasets that may contain customer records.
The system should use official and allowed access methods for external platforms, and follow their usage
policies. Organizations that upload their own datasets remain responsible for ensuring they have a lawful
basis to process that data. Hawa supports them by providing controlled access and clear separation
between different clients and projects.


```
Chapter 5: Conclusion 5.3 Social, Ethical, Legal, Global, and Security
Impact
```
**4. Global Impact**
In principle, Hawa can be used by organizations in different countries and regions. This makes it
important that large scale analysis of public posts respects local expectations about privacy and
acceptable data use, and that the system remains focused on aggregate insights about brands and topics
rather than tracking or profiling specific individuals.
**5. Security Impact**
Security is important for protecting uploaded datasets, analysis results, user accounts, and integration
credentials. The system design includes controlled onboarding of organizations, role based access to
dashboards, and separation of each client’s data so that one organization cannot see another’s results.
Sensitive credentials, such as API keys and tokens for language model providers, must be stored securely
(for example, in environment variables or secret managers) and never exposed in source code
repositories. Communication between system components should use secure channels, and inputs such
as file uploads should be validated to reduce the risk of malicious content. These measures help reduce
the risk of data leaks, unauthorized access, or tampering with analytical results, and support trust in the
system.


```
References 5.3 Social, Ethical, Legal, Global, and Security Impact
```
## REFERENCES

[1] Meta AI, "Introducing Llama 3.2: Revolutionizing edge AI and vision with open, customizable
models". Available: https://ai.meta.com/blog/llama- 3 - 2 - connect- 2024 - vision-edge-mobile-devices/

[2] Meta AI, "Llama Models”. Available: https://www.llama.com/

[3] Alibaba Cloud, “Qwen 3-VL is the multimodal LLM series developed by Qwen Team, Alibaba
Cloud.”. Avaialable: https://github.com/QwenLM/Qwen3-VL

[4] Cerebras Systems and Inception, "Jais: The World's Best Arabic Large Language Model".
Available: https://www.cerebras.ai/ai-model-services

[5] Google DeepMind, "Gemini: A Family of Highly Capable Multimodal Models". Available:
https://arxiv.org/abs/2312.11805

[6] Reddit Inc., "Reddit API Documentation". Available: https://www.reddit.com/dev/api/

[7] Reddit Inc., "PRAW: The Python Reddit API Wrapper,". Available: https://praw.readthedocs.io/

[8] X Corp., "X API Documentation," Available: https://developer.x.com/en/docs

[9] F.-E. Lagrari and Y. Elkettani, "Traditional and Deep Learning Approaches for Sentiment Analysis:
A Survey", Advances in Science, Technology and Engineering Systems Journal, vol. 6, no. 5, pp. 1-7,

2021. Available: https://astesj.com/publications/ASTESJ_060501.pdf

[10] W. Zhang, Y. Deng, B. Liu, S. J. Pan, and L. Bing, "Sentiment Analysis in the Era of Large
Language Models: A Reality Check", Findings of the Association for Computational Linguistics:
NAACL 2024, pp. 3881-3906, 2024. Available: https://aclanthology.org/2024.findings-naacl.246/

[11] Meta AI, "Llama-3.2-3B-Instruct-4bit”. Available: https://huggingface.co/mlx-community/Llama-
3.2-3B-Instruct-4bit

[12] Saudi Data and AI Authority (SDAIA), "ALLaM-7B-Instruct-preview-Q4_K_M-GGUF".
Available: https://huggingface.co/Omartificial-Intelligence-Space/ALLaM-7B-Instruct-preview-
Q4_K_M-GGUF

[13] Cerebras Systems and Inception, "jais-adapted-7b-chat-Q4_K_M-GGUF". Available:
https://huggingface.co/Solshine/jais-adapted-7b-chat-Q4_K_M-GGUF

[14] I. Sommerville, "Software Engineering", 10th ed., Pearson, 2015.

[ 15 ] snscrape, “A social networking service scraper in Python”. Available:
https://github.com/JustAnotherArchivist/snscrape


