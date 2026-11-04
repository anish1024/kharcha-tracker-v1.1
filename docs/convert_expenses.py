#!/usr/bin/env python3
import csv
import json
import uuid
from datetime import datetime
from pathlib import Path
import re

# ================= CONFIGURATION =================
import_csv = "/Users/anishnair/Projects/kharcha-tracker-v1.1/docs/expenses.csv"  # Path to input CSV file
group_id = "fc027598-ad4c-4fe0-85ae-173b61aae4cd"  # Group UUID
anish_user_id = "9e91fe5f-2b10-4624-b01f-990f90cb5473"  # Anish's user ID
lakshmi_user_id = "d98233bb-a847-4a77-a1ae-93fd51e39a4d"  # Lakshmi D V's user ID
output_file = "/Users/anishnair/Projects/kharcha-tracker-v1.1/docs/expenses.json"  # Path to output JSON file
# ===================================================

# ================= HELPER FUNCTIONS =================

def parse_csv_row_to_expense_row(row):
    """Convert a CSV row to a structured expense row with user contributions."""
    def safe_float(value):
        try:
            val = value.strip()
            return float(val) if val else 0.0
        except ValueError:
            return 0.0
    return {
        "Date": row.get("Date", "").strip(),
        "Description": row.get("Description", "").strip(),
        "Category": row.get("Category", "").strip(),
        "Cost": safe_float(row["Cost"]),
        "Currency": row.get("Currency", "INR"),
        "Lakshmi D V": safe_float(row["Lakshmi D V"]),
        "Anish": safe_float(row["Anish"]),
    }

def generate_uuid():
    """Generate a new UUID."""
    return str(uuid.uuid4())

def parse_date_to_iso(date_str):
    """Convert CSV date format to ISO 8601 datetime."""
    # Expected format: YYYY-MM-DD
    dt = datetime.strptime(date_str, "%Y-%m-%d")
    return dt.strftime("%Y-%m-%dT%H:%M:%S.%f") + "Z"

def map_category(cat: str) -> str:
    """Map CSV category to expense category enum value."""
    cat_lower = cat.lower().strip().strip('"').strip(" ")
    
    # Operating expense (payment between users)
    if cat_lower in ["payment", "paid"]:
        return "OP_EXP"
    
    # Capital expense
    if cat_lower in ["furniture", "electronics", "car", "bicycle", "repair", "maintenance"]:
        return "CAP_EXP"
    
    # Accommodation
    if cat_lower in ["rent", "accommodation", "hotel", "lodging"]:
        return "ACCOMMODATION"
    
    # Travel (cab, bus, train, fuel/gas)
    if cat_lower in ["taxi", "cab", "bus", "train", "bus/train", "gas", "fuel", "petrol"]:
        return "TRAVEL"
    
    # Entertainment
    if cat_lower in ["movies", "cinema", "concert", "theatre"]:
        return "ENTERTAINMENT"
    
    # Food & Dining
    if cat_lower in ["food", "dining out", "lunch", "dinner", "snacks"]:
        return "FOOD_N_DINING"

    # Grocery
    if cat_lower in ["grocery", "groceries"]:
        return "GROCERY"
    
    # Utility
    if cat_lower in ["electricity", "water", "gas", "heat", "internet", "phone", "mobile", "maintenance", "cleaning"]:
        return "UTILITY"
    
    # Custom
    return "CUSTOM"

def calculate_expn_split_amount(amount, lakshmi_amount, anish_amount):
    """Calculate expense split based on user contributions."""
    lakshmi_share = 0.0
    anish_share = 0.0
    split_list = []

    # Calculate each user's share
    if lakshmi_amount != 0:
        lakshmi_share = abs(lakshmi_amount)
        split_list.append({
            "user_id": lakshmi_user_id,
            "percentage": 100.0,
        })
    elif anish_amount != 0:
        anish_share = abs(anish_amount)
        split_list.append({
            "user_id": anish_user_id,
            "percentage": 100.0,
        })
    else:
        # Default 50/50 split
        share = amount / 2.0
        split_list.append({
            "user_id": lakshmi_user_id,
            "percentage": 50.0,
        })
        split_list.append({
            "user_id": anish_user_id,
            "percentage": 50.0,
        })

    return split_list

def calculate_paid_by(amount, lakshmi_amount, anish_amount, lakshmi_user_id, anish_user_id):
    """Calculate who paid the expense based on positive amounts (they paid)."""
    paid_list = []

    # Check Lakshmi D V's amount (positive means they paid)
    if type(lakshmi_amount) == float and lakshmi_amount > 0:
        paid_list.append({
            "user_id": lakshmi_user_id,
            "amt": abs(lakshmi_amount),
        })

    # Check Anish's amount (positive means he paid)
    if type(anish_amount) == float and anish_amount > 0:
        paid_list.append({
            "user_id": anish_user_id,
            "amt": abs(anish_amount),
        })

    return paid_list

def calculate_expn_split(expense_row, lakshmi_user_id, anish_user_id):
    """Calculate expense split based on user contributions."""
    return calculate_expn_split_amount(expense_row["Cost"], expense_row["Lakshmi D V"], expense_row["Anish"])

def format_expense_record(expense_row, trans_id, group_id, lakshmi_user_id, anish_user_id):
    """Convert an expense row to the expense_record JSON format."""
    # Calculate split and paid_by

    return {
        "is_deleted": False,
        "created_by": anish_user_id,
        "created_at": parse_date_to_iso(expense_row["Date"]),
        "modified_by": anish_user_id,
        "modified_at": parse_date_to_iso(expense_row["Date"]),
        "id": generate_uuid(),
        "trans_id": trans_id,
        "group_id": group_id,
        "date_time": parse_date_to_iso(expense_row["Date"]),
        "expn_ctgry": map_category(expense_row["Category"]),
        "expn_desc": expense_row["Description"],
        "expn_amnt": expense_row["Cost"],
        "expn_splt": calculate_expn_split_amount(expense_row["Cost"], expense_row["Lakshmi D V"], expense_row["Anish"]),
        "paid_by": calculate_paid_by(expense_row["Cost"], expense_row["Lakshmi D V"], expense_row["Anish"], lakshmi_user_id, anish_user_id),
        "expn_bill_screenshot": None,
        "expn_payment_screenshot": None,
        "status": "Open",
    }

def calculate_expn_split_amount(amount, lakshmi_amount, anish_amount):
    """Calculate expense split based on user contributions."""
    split_list = []

    lakshmi_share = lakshmi_amount if lakshmi_amount > 0 else lakshmi_amount
    anish_share = anish_amount if anish_amount > 0 else anish_amount

    # Handle edge case where total_amount is 0
    if amount == 0:
        split_list.append({
            "user_id": lakshmi_user_id,
            "percentage": 50.0,
        })
        split_list.append({
            "user_id": anish_user_id,
            "percentage": 50.0,
        })
    else:
        # Calculate percentages based on contributions
        lakshmi_pct = abs(lakshmi_share)
        anish_pct = abs(anish_share)
        total_contributions = lakshmi_pct + anish_pct

        split_list.append({
            "user_id": lakshmi_user_id,
            "percentage": round((lakshmi_pct / total_contributions) * 100, 1) if total_contributions > 0 else 50.0,
        })
        split_list.append({
            "user_id": anish_user_id,
            "percentage": round((anish_pct / total_contributions) * 100, 1) if total_contributions > 0 else 50.0,
        })

    return split_list

# ================= MAIN SCRIPT =================

def convert_expenses():
    """Main function to convert expenses.csv to expenses.json."""
    input_file = Path(import_csv)
    output_path = Path(output_file)

    if not input_file.exists():
        raise FileNotFoundError(f"Input file not found: {import_csv}")

    expenses = []
    trans_id = 1000

    with open(input_file, "r") as f:
        reader = csv.DictReader(f)
        trans_id_counter = 0

        for row in reader:
            # Parse the CSV row
            expense_row = parse_csv_row_to_expense_row(row)

            # Generate the expense record
            expense_record = format_expense_record(expense_row, trans_id_counter, group_id, lakshmi_user_id, anish_user_id)

            expenses.append(expense_record)
            trans_id_counter += 1

    # Write to output JSON file
    with open(output_path, "w") as f:
        json.dump(expenses, f, indent=2)

    print(f"Successfully converted {len(expenses)} expense records to {output_file}")

if __name__ == "__main__":
    convert_expenses()