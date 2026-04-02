# Fee Waiver & Home Loan Closure Feature

## Overview

This feature enables AI-powered agent assistance for two banking operations:
1. **Credit Card Fee Waiver** - Help agents guide customers through fee waiver eligibility
2. **Home Loan Force Closure** - Help agents guide customers through loan foreclosure process

The system combines:
- **Static Checklist** (in AI prompt) - Step-by-step guide for each operation
- **Dynamic Customer Data** (from Salesforce API) - Real-time customer information

---

## Operations

### 1. Credit Card Fee Waiver

**Detection:** AI detects from conversation (e.g., "I want to waive my annual fee", "credit card fee waiver")

**Trigger:** Mobile number from conversation

### 2. Home Loan Force Closure

**Detection:** AI detects from conversation (e.g., "I want to close my home loan", "loan foreclosure")

**Trigger:** Mobile number from conversation

---

## Checklist Guide (Static - In AI Prompt)

```
METROBANK CHECKLIST GUIDE

CREDIT CARD FEE WAIVER CHECKLIST
================================

For New Cardholders (Welcome Promos):
-------------------------------------
No Annual Fee for Life (NAFFL):
- Spend ₱30,000 within 90 days (Titanium Mastercard)
- Enroll in the Metrobank App
- For Rewards Plus / Cashback Visa: maintain ₱180,000 – ₱250,000 annual spend

Activation Requirement:
- Activate the card within 60 days of approval
- Use the card at least once

For Existing Cardholders (Manual Reversal):
-------------------------------------------
Call: (02) 88-700-700

Options:
- Spend required amount (e.g., ₱20,000 within 30 days)
- Redeem reward points (e.g., 30,000 points = ₱2,500 fee)
- Enroll a bill in Bills2Pay
- Apply for a supplementary card (first is usually free)


HOME LOAN CLOSURE & FORECLOSURE CHECKLIST
=========================================

Loan Payoff (Redemption):
-------------------------
- Email: CLOD-ASFD@metrobank.com.ph
- Request Statement of Account (SOA)
- Provide name, loan account number, and target payment date

Before Payment:
---------------
- Check for pre-termination fee
- Confirm any charge-back fees (DST, mortgage registration, etc.)

After Full Payment:
-------------------
- Collect Original Owner's Duplicate TCT
```

---

## Salesforce API Integration

### Credit Card Fee Waiver API

**Endpoint:**
```
GET https://salesforce.lab.bravishma.com/api/v4/credit-cards?mobileNumber={phone}
```

**Example Request:**
```bash
curl -X 'GET' \
  'https://salesforce.lab.bravishma.com/api/v4/credit-cards?mobileNumber=%2B918888047520' \
  -H 'accept: */*'
```

**Response:**
```json
{
  "success": true,
  "customer": {
    "Id": "a04gK000009z6UoQAI",
    "Full_Name__c": "Abhinandan patl",
    "Phone__c": "+918888047520"
  },
  "data": [
    {
      "attributes": {
        "type": "Credit_Card__c",
        "url": "/services/data/v57.0/sobjects/Credit_Card__c/a05gK00002fImTPQA0"
      },
      "Id": "a05gK00002fImTPQA0",
      "Card_ID__c": "CC-001",
      "Card_Number__c": "XXXX-XXXX-XXXX-1234",
      "Card_Type__c": "Rewards",
      "Card_Status__c": "Active",
      "Credit_Limit__c": 500000,
      "Annual_Fee__c": 1500,
      "NAFFL_Eligible__c": true,
      "Activation_Date__c": "2026-01-15",
      "Delinquency_Status__c": "Good",
      "Total_Spend__c": 125000,
      "Eligible_Spend_90D__c": 45000,
      "Eligible_Spend_12M__c": 120000,
      "Last_Transaction_Date__c": null,
      "Customer__c": "a04gK000009z6UoQAI",
      "CreatedDate": "2026-03-18T09:42:05.000+0000",
      "LastModifiedDate": "2026-03-18T09:42:05.000+0000"
    }
  ]
}
```

**Key Fields for Checklist Matching:**

| Field | Purpose | Checklist Rule |
|-------|---------|----------------|
| `Card_Type__c` | Card type (Titanium, Rewards, Cashback) | Different spend requirements per type |
| `NAFFL_Eligible__c` | NAFFL eligibility flag | Check if eligible for No Annual Fee for Life |
| `Eligible_Spend_90D__c` | Spend in last 90 days | Must be ≥ ₱30,000 for Titanium NAFFL |
| `Eligible_Spend_12M__c` | Annual spend | Must be ₱180,000–₱250,000 for Rewards/Cashback |
| `Activation_Date__c` | Card activation date | Must activate within 60 days of approval |
| `Annual_Fee__c` | Annual fee amount | The fee to be waived |
| `Delinquency_Status__c` | Payment status | Good/Past Due/Default |
| `Card_Status__c` | Card status | Active/Blocked/Closed |

---

### Home Loan Closure API

**Endpoint:**
```
GET https://salesforce.lab.bravishma.com/api/v4/home-loans?mobileNumber={phone}
```

**Example Request:**
```bash
curl -X 'GET' \
  'https://salesforce.lab.bravishma.com/api/v4/home-loans?mobileNumber=%2B918888047520' \
  -H 'accept: */*'
```

**Response:**
```json
{
  "success": true,
  "customer": {
    "Id": "a04gK000009z6UoQAI",
    "Full_Name__c": "Abhinandan patl",
    "Phone__c": "+918888047520"
  },
  "data": [
    {
      "attributes": {
        "type": "Home_Loan__c",
        "url": "/services/data/v57.0/sobjects/Home_Loan__c/a06gK00000DUnILQA1"
      },
      "Id": "a06gK00000DUnILQA1",
      "Loan_ID__c": "HL-001",
      "Loan_Account_Number__c": "HLAC000123",
      "Loan_Status__c": "Active",
      "Sanctioned_Amount__c": 5000000,
      "Outstanding_Amount__c": 4500000,
      "Interest_Rate__c": 8.5,
      "Loan_Start_Date__c": "2025-06-01",
      "Lockin_Period_End_Date__c": "2026-06-01",
      "Foreclosure_Allowed__c": true,
      "Prepayment_Penalty__c": 2,
      "Legal_Status__c": "Clear",
      "Last_Payment_Date__c": null,
      "EMI_Status__c": "Regular",
      "Customer__c": "a04gK000009z6UoQAI",
      "CreatedDate": "2026-03-18T09:42:30.000+0000",
      "LastModifiedDate": "2026-03-18T09:42:30.000+0000"
    }
  ]
}
```

**Key Fields for Checklist Matching:**

| Field | Purpose | Checklist Rule |
|-------|---------|----------------|
| `Loan_Status__c` | Loan status | Must be Active for closure |
| `Outstanding_Amount__c` | Amount to pay off | Total payoff amount |
| `Foreclosure_Allowed__c` | Foreclosure allowed flag | Must be true for closure |
| `Prepayment_Penalty__c` | Penalty percentage | Pre-termination fee (e.g., 2%) |
| `Lockin_Period_End_Date__c` | Lock-in period end | Check if still in lock-in |
| `Legal_Status__c` | Legal status | Clear/Dispute/Litigation |
| `EMI_Status__c` | EMI payment status | Regular/Delayed/Default |
| `Loan_Account_Number__c` | Account number | Needed for SOA request |

---

## Expected AI Behavior

### Fee Waiver Scenario

**Customer:** "I want to waive my annual fee"

**AI Process:**
1. Detects intent: Credit Card Fee Waiver
2. Fetches credit card data via mobile number
3. Analyzes against checklist:
   - `Eligible_Spend_90D__c: 45000` → Exceeds ₱30,000 requirement ✓
   - `NAFFL_Eligible__c: true` → Eligible ✓
   - `Card_Type__c: Rewards` → Check annual spend requirement

**Suggested Response:**
> "Customer has spent ₱45,000 in the last 90 days (exceeds ₱30,000 NAFFL requirement).
> Card is NAFFL eligible. Card Type: Rewards (requires ₱180,000–₱250,000 annual spend).
> Annual spend: ₱120,000 (needs ₱60,000 more for annual requirement).
>
> **Recommend:** Confirm NAFFL enrollment based on 90-day spend criteria, OR advise customer
> to increase annual spend to ₱180,000 for Rewards card benefit."

---

### Home Loan Closure Scenario

**Customer:** "I want to close my home loan"

**AI Process:**
1. Detects intent: Home Loan Closure
2. Fetches home loan data via mobile number
3. Analyzes against checklist:
   - `Foreclosure_Allowed__c: true` → Allowed ✓
   - `Prepayment_Penalty__c: 2%` → 2% penalty applies
   - `Outstanding_Amount__c: 4500000` → ₱45,00,000 to pay
   - `Lockin_Period_End_Date__c: 2026-06-01` → Still in lock-in period
   - `Legal_Status__c: Clear` → No legal issues ✓

**Suggested Response:**
> "Foreclosure is allowed for this loan.
>
> **Loan Details:**
> - Outstanding Amount: ₱45,00,000
> - Pre-termination Penalty: 2% (₱90,000)
> - Lock-in Period: Ends June 2026 (currently in lock-in)
> - Legal Status: Clear
> - EMI Status: Regular
>
> **Next Steps:**
> 1. Inform customer about 2% prepayment penalty (₱90,000)
> 2. Customer should email CLOD-ASFD@metrobank.com.ph for SOA
> 3. Provide: Name, Loan Account Number (HLAC000123), Target payment date
> 4. After payment, customer will receive Original Owner's Duplicate TCT"

---

## Data Models (DTOs)

### Credit Card DTOs

```java
// Request - uses mobile number (already available)

// Response
public class CreditCardResponse {
    private boolean success;
    private SalesforceCustomer customer;
    private List<CreditCard> data;
}

public class CreditCard {
    private String id;
    private String cardId;           // Card_ID__c
    private String cardNumber;       // Card_Number__c (masked)
    private String cardType;         // Card_Type__c
    private String cardStatus;       // Card_Status__c
    private BigDecimal creditLimit;  // Credit_Limit__c
    private BigDecimal annualFee;    // Annual_Fee__c
    private boolean nafflEligible;   // NAFFL_Eligible__c
    private LocalDate activationDate; // Activation_Date__c
    private String delinquencyStatus; // Delinquency_Status__c
    private BigDecimal totalSpend;    // Total_Spend__c
    private BigDecimal eligibleSpend90D; // Eligible_Spend_90D__c
    private BigDecimal eligibleSpend12M; // Eligible_Spend_12M__c
    private LocalDateTime lastTransactionDate; // Last_Transaction_Date__c
}
```

### Home Loan DTOs

```java
// Response
public class HomeLoanResponse {
    private boolean success;
    private SalesforceCustomer customer;
    private List<HomeLoan> data;
}

public class HomeLoan {
    private String id;
    private String loanId;              // Loan_ID__c
    private String loanAccountNumber;   // Loan_Account_Number__c
    private String loanStatus;          // Loan_Status__c
    private BigDecimal sanctionedAmount; // Sanctioned_Amount__c
    private BigDecimal outstandingAmount; // Outstanding_Amount__c
    private BigDecimal interestRate;    // Interest_Rate__c
    private LocalDate loanStartDate;    // Loan_Start_Date__c
    private LocalDate lockinPeriodEndDate; // Lockin_Period_End_Date__c
    private boolean foreclosureAllowed; // Foreclosure_Allowed__c
    private BigDecimal prepaymentPenalty; // Prepayment_Penalty__c (percent)
    private String legalStatus;         // Legal_Status__c
    private LocalDate lastPaymentDate;  // Last_Payment_Date__c
    private String emiStatus;           // EMI_Status__c
}
```

---

## Implementation (Completed)

### 1. DTOs Created
- `src/main/java/com/agentassist/dto/salesforce/CreditCard.java` - Credit card entity from Salesforce
- `src/main/java/com/agentassist/dto/salesforce/CreditCardResponse.java` - API response wrapper
- `src/main/java/com/agentassist/dto/salesforce/CustomerCreditCardData.java` - Processed data for AI context
- `src/main/java/com/agentassist/dto/salesforce/HomeLoan.java` - Home loan entity from Salesforce
- `src/main/java/com/agentassist/dto/salesforce/HomeLoanResponse.java` - API response wrapper
- `src/main/java/com/agentassist/dto/salesforce/CustomerHomeLoanData.java` - Processed data for AI context
- `src/main/java/com/agentassist/dto/salesforce/SalesforceCustomer.java` - Common customer info

### 2. Salesforce Client Updated
- `src/main/java/com/agentassist/service/salesforce/SalesforceClient.java`
  - Added `getCustomerCreditCardData(String mobileNumber)` method
  - Added `getCustomerHomeLoanData(String mobileNumber)` method

### 3. Checklist Service Created
- `src/main/java/com/agentassist/service/checklist/ChecklistService.java`
  - Detects operation type (FEE_WAIVER, HOME_LOAN_CLOSURE) from conversation
  - Fetches appropriate Salesforce data based on detected operation
  - Contains static checklist guides for both operations
  - Returns `ChecklistContext` with checklist + customer data

- `src/main/java/com/agentassist/service/checklist/ChecklistCacheService.java`
  - Caches checklist context per conversation (30 min TTL)

### 4. AI Provider Updated
- `src/main/java/com/agentassist/ai/AiProvider.java` - Added `analyzeConversationWithChecklist()` method
- `src/main/java/com/agentassist/ai/BaseAiProvider.java` - Implemented checklist-aware prompt

### 5. Services Updated
- `src/main/java/com/agentassist/service/analysis/AnalysisService.java` - Added `analyzeConversationWithChecklist()`
- `src/main/java/com/agentassist/service/processing/ConversationProcessingService.java`
  - Integrated checklist detection in message processing flow
  - Uses checklist-aware AI analysis when operation detected

### 6. Response DTO Updated
- `src/main/java/com/agentassist/dto/responseDTO/OneShotResponse.java`
  - Added `usedChecklist` boolean
  - Added `checklistOperation` string (FEE_WAIVER, HOME_LOAN_CLOSURE)
  - Added `creditCardsFound` int
  - Added `homeLoansFound` int

---

## How It Works

1. **Customer sends message** (e.g., "I want to waive my annual fee")
2. **System processes message** → detects language, translates to English
3. **AI detects operation type** → Understands intent in any language/phrasing
4. **Salesforce data fetched** (credit cards or home loans based on operation)
5. **AI generates suggestions** using checklist guide + customer's actual data
6. **Response includes** checklist-aware suggestions with specific numbers

## AI-Based Operation Detection

The system uses AI to detect what the customer is asking about. This is more accurate than regex patterns.

**AI Prompt:**
```
Analyze the conversation and determine what the customer is asking about.
Return ONLY one of: FEE_WAIVER, HOME_LOAN_CLOSURE, GENERAL
```

**Benefits over regex:**
- Handles any language (Hindi, English, etc.)
- Understands context and variations
- No need to maintain keyword patterns
- Catches indirect requests

**Examples AI can detect:**
| Customer Says | AI Detects |
|---------------|------------|
| "I want fee waiver" | FEE_WAIVER |
| "The yearly charges are too high" | FEE_WAIVER |
| "मुझे फीस माफ करनी है" (Hindi) | FEE_WAIVER |
| "Can something be done about card charges?" | FEE_WAIVER |
| "I want to settle my housing loan" | HOME_LOAN_CLOSURE |
| "Loan band karna hai" (Hindi) | HOME_LOAN_CLOSURE |
| "What is your refund policy?" | GENERAL → RAG |

---

## Testing

To test the feature:

1. Start the application: `mvn spring-boot:run`
2. Send a message via POST `/api/v1/agent-assistant/process`:
```json
{
  "interactionId": "test-123",
  "from": "customer",
  "message": "I want to waive my credit card annual fee",
  "mobileNumber": "+918888047520"
}
```

3. Response should include:
   - `usedChecklist: true`
   - `checklistOperation: "FEE_WAIVER"`
   - `creditCardsFound: 1`
   - Suggestions with specific spend amounts and NAFFL eligibility
