# Auto Parts Warranty Policy

> **SYNTHETIC TEST FIXTURE**: This document is a synthetic test fixture created exclusively for automated RAG testing and system evaluation. It does not represent actual company policies, legal agreements, or binding terms.

## 1. Overview and Scope

This document defines the warranty terms, eligibility criteria, and claim submission processes for automotive replacement components sold across retail branches and commercial trade accounts. The warranty coverage protects customers against verified material defects and manufacturing flaws under normal operating conditions.

Warranty coverage begins on the calendar date of customer purchase indicated on the original computer-generated tax invoice. Warranty benefits apply exclusively to the original retail or trade purchaser and are strictly non-transferable to subsequent vehicle owners.

## 2. Product Warranty Categories

### 2.1 Automotive Batteries

Lead-acid, Absorbent Glass Mat (AGM), and Enhanced Flooded Batteries (EFB) are covered for a period of 24 months from the invoice date.
- **Free Replacement Period**: If a battery tests defective within the first 12 months, the branch will issue an identical brand-new replacement battery at zero charge.
- **Pro-Rata Period**: Defects occurring between months 13 and 24 are eligible for a 50% pro-rata credit calculated against the prevailing retail price toward the purchase of a replacement unit.
- **Testing Requirement**: Every battery presented for warranty claim must undergo an automated conductance and load test using the branch battery analyzer. The automated test receipt showing "REPLACE BATTERY" must be attached to the claim docket.
- **Exclusions**: Discharged batteries that recover full charge upon bench charging, cracked plastic casings, frozen electrolyte cells, inverted polarity connections, and melted terminal posts resulting from loose cable clamps are strictly excluded from warranty coverage.

### 2.2 Brake Rotors and Friction Components

Brake rotors, brake drums, and premium ceramic/semi-metallic brake pads carry a 12-month or 15,000-kilometer limited defect warranty, whichever threshold occurs first.
- **Defect Eligibility**: Coverage applies solely to manufacturing defects such as casting porosity, structural cracking under standard brake torque, and rotor runout exceeding 0.05 mm measured upon initial bed-in.
- **Mandatory Exclusions**: Uneven pad wear resulting from seized caliper slide pins, friction lining glaze caused by abusive heat cycling, normal friction material reduction, and structural grooving caused by foreign road debris or metal-to-metal contact are explicitly excluded.
- **Commercial Fleet Limitation**: Vehicles operating in taxi, rideshare, delivery, or motorsport competitions carry a restricted 90-day warranty window on friction components.

### 2.3 Alternators and Starters

Remanufactured and OEM-grade alternators and starter motors carry a 36-month limited warranty covering mechanical failure and electrical malfunction.
- **Eligibility**: Coverage includes internal diode bridge failure, defective voltage regulation, bearing seizure, and armature open circuits under standard 12-volt and 24-volt electrical systems.
- **Diagnostic Requirement**: The customer must present the vehicle or the removed component for a bench test verification demonstrating defective electrical output (charging voltage below 13.5V under load or shorted rotor windings).
- **Core Unit Verification**: Warranty replacement requires surrender of the failed unit with intact aluminum housings. Units with melted terminal studs or external fire damage will be rejected.

### 2.4 Electrical Sensors and Control Modules

Electronic sensors (including oxygen sensors, mass airflow sensors, crankshaft position sensors, and ABS wheel speed sensors) carry a 12-month replacement warranty.
- **Installation Standard**: Sensors must have been installed by a certified automotive technician using proper torque specifications and clean electrical harness connectors.
- **Burnout Exclusion**: Electronic components exhibiting terminal discoloration, liquid immersion corrosion, over-voltage spikes resulting from faulty jump-starts, or spliced wiring harnesses are not eligible for warranty reimbursement.
- **Diagnostic Report**: The claim docket must include a scan tool diagnostic report showing active Diagnostic Trouble Codes (DTCs) that correspond directly to the sensor circuit failure.

## 3. General Warranty Exclusions

The following conditions void all warranty protections across all automotive component categories:
1. Unauthorized modification, disassembly, racing, off-road competition, or improper vehicle fitment.
2. Failure to comply with scheduled maintenance intervals recommended by the original vehicle manufacturer.
3. Collateral damage caused by the secondary failure of unrelated automotive systems (e.g., alternator destroyed by continuous oil contamination from an unresolved engine valve cover leak).
4. Physical damage resulting from road collisions, stone strikes, water ingress, or improper tool usage during installation.
5. Inability of the claimant to provide the original purchase invoice number and matching product serial number.

## 4. Warranty Claim Submission Procedure

To initiate a warranty claim, the customer or workshop technician must complete the following mandatory steps:
1. **Present Original Documentation**: Provide the original physical or digital tax invoice displaying the branch code, item code, date of purchase, and serial number.
2. **Defective Part Surrender**: Surrender the complete defective component without cleaning off oil deposits or disassembling internal mechanisms.
3. **Claim Form Completion**: Fill out Form WAR-01 at the customer service counter, noting the vehicle make, model year, Vehicle Identification Number (VIN), odometer reading at installation, and odometer reading at failure.
4. **Diagnostic Attachment**: Attach bench test printouts, battery analyzer slips, or electronic diagnostic scan reports where applicable.

## 5. Review and Approval Workflow

1. **Counter Inspection**: The counter cashier inspects the physical part within 30 minutes of surrender to verify serial authenticity and check for obvious physical abuse or fluid contamination.
2. **Technical Verification**: The branch technical supervisor conducts mandatory component testing within 24 business hours. If the component passes electrical/bench verification, the claim is rejected and the part is returned to the customer.
3. **Manager Sign-Off**: Once defect confirmation is established, the branch manager enters the claim authorization in the ERP system under the `WAR_APPROVAL` module.
4. **Resolution Issuance**: The customer is issued an identical replacement unit immediately from current warehouse stock. If stock is unavailable, an authorized store credit note is issued for the full invoice value. Cash refunds are strictly prohibited for warranty claim settlements.
