# ERP Billing & GST Clarification FAQ

> **SYNTHETIC TEST FIXTURE**: This FAQ is a synthetic educational test fixture created solely for automated testing of ERP billing workflows and RAG query retrieval. It does NOT constitute professional accounting, financial, or tax advice.

## 1. Taxable Value versus Subtotal Concepts

### 1.1 What is the exact difference between Gross Subtotal and Taxable Value in ERP invoices?
In the Auto Parts ERP billing engine, the **Gross Subtotal** represents the sum of itemized unit prices multiplied by quantities before any line-item or invoice-level trade discounts are applied.

The **Taxable Value** (also referred to as the assessable value) is the net base amount upon which Goods and Services Tax (GST) is legally computed. It is calculated using the formula:
$$\text{Taxable Value} = \text{Gross Subtotal} - \text{Trade Discounts} + \text{Incidental Packing / Freight Charges}$$

Under standard invoicing rules, cash discounts offered *after* the supply takes place do not reduce the taxable value unless agreed upon before or at the time of supply and established by linked invoices.

### 1.2 Does the retail Maximum Retail Price (MRP) include GST?
Yes. Consumer-facing Maximum Retail Price (MRP) stickers displayed on aftermarket spare parts packaging are statutory inclusive of all applicable taxes. When an item is sold on an MRP-inclusive basis, the ERP point-of-sale system automatically back-calculates the taxable value using the formula:
$$\text{Taxable Value} = \frac{\text{MRP}}{1 + (\text{GST Rate Percentage} / 100)}$$
The resulting taxable amount and the extracted GST components are explicitly segregated on the customer tax invoice receipt.

## 2. Harmonized System of Nomenclature (HSN) Codes

### 2.1 Why are HSN codes mandatory on auto parts invoices?
Harmonized System of Nomenclature (HSN) codes are international 6-to-8 digit commodity classification numbers that determine the statutory GST tax rate applicable to automotive components. In the Auto Parts ERP system, every product master record must be linked to an approved HSN code before inventory receipts or sales invoices can be generated.

### 2.2 What are the primary HSN codes used in our automotive parts inventory?
The following synthetic illustrative HSN classifications are configured in our standard product catalog:
- **HSN 8708**: Parts and accessories of motor vehicles (chassis frames, brake rotors, brake calipers, radiators, silencers, suspension shock absorbers, steering gears, and road wheels) — standard 28% GST slab.
- **HSN 8511**: Electrical ignition or starting equipment (spark plugs, ignition coils, starter motors, alternators, and glow plugs) — standard 18% GST slab.
- **HSN 8507**: Electric accumulators, including automotive lead-acid and AGM batteries — standard 28% GST slab.
- **HSN 8421**: Centrifugal liquid and air filtration machinery (engine oil filters, intake air filters, fuel filters, and cabin filtration elements) — standard 18% GST slab.
- **HSN 4016**: Vulcanized rubber products (engine drive belts, radiator hoses, suspension rubber bushings, and oil seals) — standard 18% GST slab.

### 2.3 What level of HSN digit precision is required by the ERP?
For businesses with annual aggregate turnover exceeding 50 million currency units, the ERP strictly enforces mandatory 6-digit or 8-digit HSN reporting on all business-to-business (B2B) tax invoices. For business-to-consumer (B2C) counter retail receipts, 4-digit HSN printing is permissible.

## 3. GST Tax Breakdown and Slab Calculation

### 3.1 How does the ERP determine whether to apply CGST/SGST or IGST?
The tax determination depends on the **Place of Supply** relative to the supplier's registered warehouse location:
- **Intra-State Supply** (Buyer and seller located in the same state/province): The GST rate is divided equally between Central GST (CGST) and State GST (SGST). For example, a 28% automotive battery carries 14% CGST and 14% SGST.
- **Inter-State Supply** (Buyer and seller located in different states/provinces): The full combined tax percentage is levied as Integrated GST (IGST). For example, a 28% battery shipped across state borders carries 28% IGST.

### 3.2 How does the system handle invoice rounding?
In accordance with tax rounding regulations, line-item tax amounts are calculated to four decimal places internally. The final aggregate invoice tax amount and total invoice payable are rounded to the nearest integer currency unit (amounts of 0.50 and above round up; amounts below 0.50 round down). The round-off differential is recorded in the dedicated `INVOICE_ROUNDING` ledger account.

## 4. Debit Note and Credit Note Workflows

### 4.1 When must a Credit Note be issued rather than modifying an invoice?
Once a tax invoice has been validated and finalized in the ERP, statutory regulations strictly forbid deleting, altering, or voiding the invoice record. A **Credit Note** must be issued in the following scenarios:
1. **Sales Returns**: When a customer returns goods within the authorized 30-day return window.
2. **Post-Sale Rate Reductions**: When a commercial trade partner is granted a volume discount or price adjustment after invoice generation.
3. **Damaged Goods Allowance**: When parts arrive at the customer workshop damaged and a partial credit is negotiated without returning the physical part.

The Credit Note reduces the seller's tax liability and must reference the original invoice number, original date of supply, and exact HSN code quantities being adjusted.

### 4.2 When is a Debit Note required in the ERP?
A **Debit Note** is issued by the supplier to the customer when the taxable value or tax charged in the original invoice is found to be lower than the actual amount due (e.g., incorrect price entered at counter checkout, or additional freight charges incurred post-billing). Alternatively, our purchasing department issues a Debit Note to an external parts manufacturer when returning rejected vendor shipments for financial reimbursement.
