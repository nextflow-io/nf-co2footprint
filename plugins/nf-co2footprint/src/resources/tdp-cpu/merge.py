import csv
import os

def read_and_process_amd(processors, file_path):
    # Define the headers we're interested in
    headers = [
        "Name", "Default TDP", "# of CPU Cores", "# of Threads"
    ]

    try:
        with open(file_path, 'r', encoding="utf-8-sig") as csv_file:
            csv_reader = csv.DictReader(csv_file)

            for header in headers:
                if header not in csv_reader.fieldnames:
                    raise ValueError(f"Missing required header: {header}")
                
            # Process each row in the CSV
            for row in csv_reader:
                try:
                    processor_model = row["Name"].replace("AMD", "").replace("™","").strip()
                    
                    tdp = row["Default TDP"].strip().replace("W", "").replace("+", "")
                    n_cores = row["# of CPU Cores"].strip()
                    n_threads = row["# of Threads"].strip()
                    if n_cores == "" or n_threads == "" or tdp == "":
                        continue

                    if "-" in tdp:
                        tdp_splitted = tdp.split("-")
                        tdp = (float(tdp_splitted[0]) + float(tdp_splitted[1])) / 2.0
                    else:
                        tdp = float(tdp)
                    n_cores = int(n_cores)
                    n_threads = int(n_threads)

                    tdp_per_core = tdp / n_cores if n_cores > 0 else 0
                    tdp_per_thread = tdp / n_threads if n_threads > 0 else 0
                    processor = {}
                    processor['tdp'] = tdp
                    processor['manufacturer'] = "AMD"
                    processor['model'] = processor_model
                    processor['n_cores'] = n_cores
                    processor['n_threads'] = n_threads
                    processor['tdp_per_core'] = tdp_per_core
                    processor['tdp_per_thread'] = tdp_per_thread
                    processor['source'] = "https://www.amd.com/en/products/specifications/processors.html"
                    processors[processor_model] = processor
                except ValueError as ve:
                    print(f"Error processing row {row}: {ve}")
    except FileNotFoundError:
        print(f"File not found: {file_path}")
    except ValueError as ve:
        print(f"Error: {ve}")

def read_and_process_ampere_altra(processors, file_path):
    # Define the headers we're interested in
    headers = [
        "PRODUCT NAME", "CORES", "USAGE POWER (W)"
    ]

    try:
        with open(file_path, 'r') as csv_file:
            csv_reader = csv.DictReader(csv_file)

            # Check if required headers exist in the file
            for header in headers:
                if header not in csv_reader.fieldnames:
                    raise ValueError(f"Missing required header: {header}")

            # Process each row in the CSV
            for row in csv_reader:
                try:
                    tdp = float(row["USAGE POWER (W)"].strip().replace("W", ""))
                    n_cores = int(float(row["CORES"].strip()))
                    n_threads = n_cores  # Thread count equals core count
                    processor_model = "AmpereAltra " +row["PRODUCT NAME"].strip()
                    tdp_per_core = tdp / n_cores if n_cores > 0 else 0
                    tdp_per_thread = tdp / n_threads if n_threads > 0 else 0

                    processor = {}
                    processor['tdp'] = tdp
                    processor['manufacturer'] = "Ampere"
                    processor['model'] = processor_model
                    processor['n_cores'] = n_cores
                    processor['n_threads'] = n_threads
                    processor['tdp_per_core'] = tdp_per_core
                    processor['tdp_per_thread'] = tdp_per_thread
                    processor['source'] = "https://amperecomputing.com/briefs/ampere-altra-family-product-brief"
                    processors[processor_model] = processor
                except ValueError as ve:
                    print(f"Error processing row {row}: {ve}")
    except FileNotFoundError:
        print(f"File not found: {file_path}")
    except ValueError as ve:
        print(f"Error: {ve}")
    return processors



def read_and_process_ampere_one(processors, file_path):
    # Define the headers we're interested in
    headers = [
        "Processor Model", "Core Count", "Usage Power*"
    ]

    try:
        with open(file_path, 'r') as csv_file:
            csv_reader = csv.DictReader(csv_file)

            # Check if required headers exist in the file
            for header in headers:
                if header not in csv_reader.fieldnames:
                    raise ValueError(f"Missing required header: {header}")

            # Process each row in the CSV
            for row in csv_reader:
                try:
                    processor_model = row["Processor Model"].replace("®","").strip()
                    tdp = float(row["Usage Power*"].strip().replace("W", ""))
                    n_cores = int(row["Core Count"].strip())
                    n_threads = n_cores  # Thread count equals core count
     
                    tdp_per_core = tdp / n_cores if n_cores > 0 else 0
                    tdp_per_thread = tdp / n_threads if n_threads > 0 else 0
 
                    processor = {}
                    processor['tdp'] = tdp
                    processor['manufacturer'] = "Ampere"
                    processor['model'] = processor_model
                    processor['n_cores'] = n_cores
                    processor['n_threads'] = n_threads
                    processor['tdp_per_core'] = tdp_per_core
                    processor['tdp_per_thread'] = tdp_per_thread
                    processor['source'] = "https://amperecomputing.com/briefs/ampereone-family-product-brief"
                    processors[processor_model] = processor
                except ValueError as ve:
                    print(f"Error processing row {row}: {ve}")
    except FileNotFoundError:
        print(f"File not found: {file_path}")
    except ValueError as ve:
        print(f"Error: {ve}")


def read_and_process_intel(processors, root_dir):
    for file in os.listdir(root_dir):
        file_path = os.path.join(root_dir, file)
        # Dictionary to store processor information
        processor_data = {}

        # Read the CSV file
        with open(file_path, mode='r') as csv_file:
            csv_reader = csv.reader(csv_file)

            # Skip the header rows
            header_rows = 2
            for _ in range(header_rows):
                next(csv_reader)

            # Get processor names (header row after two initial rows)
            processor_names = next(csv_reader)[1:]

            # Initialize dictionary for each processor
            for name in processor_names:
                processor_data[name] = {}

            # Read the rows to extract relevant data
            for row in csv_reader:
                # If the row contains essential data, process it
                if len(row) > 0 and row[0] in ["Product Collection", "Total Cores", "Total Threads","TDP", "Processor Base Power"]:
                    key = row[0]  # Key name from the first column
                    for i, value in enumerate(row[1:]):
                        processor_data[processor_names[i]][key] = value

            # Add processor numbers to the dictionary
            for i, name in enumerate(processor_names):
                processor_data[name]["Processor Name"] = name

        # Print the processed data
        for processor_name, details in processor_data.items():
            if "TDP" in details:
                tdp = details["TDP"].strip().replace("W", "")
            elif "Processor Base Power" in details:
                tdp = details["Processor Base Power"].strip().replace("W", "")

            if tdp == "":
                continue
            processor_model = processor_name.replace("Intel", "").replace("®","").strip()
            tdp = float(tdp)
            n_cores = int(details["Total Cores"].strip())
            n_threads = int(details["Total Threads"].strip())  # Thread count equals core count
            tdp_per_core = tdp / n_cores if n_cores > 0 else 0
            tdp_per_thread = tdp / n_threads if n_threads > 0 else 0

            processor = {}
            processor['manufacturer'] = "Intel"
            processor['model'] = processor_model
            processor['tdp'] = tdp
            processor['n_cores'] = n_cores
            processor['n_threads'] = n_threads
            processor['tdp_per_core'] = tdp_per_core
            processor['tdp_per_thread'] = tdp_per_thread
            processor['source'] = "https://www.intel.com/content/www/us/en/products/details/processors.html"
            processors[processor_model] = processor

def read_and_process_green_algorithms(processors, file_path):
    headers = [
        "model","TDP","n_cores","TDP_per_core","source","manufacturer","threads"
    ]
    try:
        with open(file_path, 'r') as csv_file:
            csv_reader = csv.DictReader(csv_file)

            # Check if required headers exist in the file
            for header in headers:
                if header not in csv_reader.fieldnames:
                    raise ValueError(f"Missing required header: {header}")

            # Process each row in the CSV
            for row in csv_reader:
                try:
                    processor_model = row["model"].strip()
                    manufacturer = row["manufacturer"].strip()
                    tdp = float(row["TDP"].strip().replace("W", ""))
                    n_cores = int(float(row["n_cores"].strip()))
                    n_threads = int(row["threads"].strip())
        
                    tdp_per_core = tdp / n_cores if n_cores > 0 else 0
                    tdp_per_thread = tdp / n_threads if n_threads > 0 else 0

                    processor = {}
                    processor['manufacturer'] = manufacturer
                    processor['model'] = processor_model
                    processor['tdp'] = tdp
                    processor['n_cores'] = n_cores
                    processor['n_threads'] = n_threads
                    processor['tdp_per_core'] = tdp_per_core
                    processor['tdp_per_thread'] = tdp_per_thread
                    processor['source'] = row["source"].strip()
                    processors[processor_model] = processor
                except ValueError as ve:
                    print(f"Error processing row {row}: {ve}")
    except FileNotFoundError:
        print(f"File not found: {file_path}")
    except ValueError as ve:
        print(f"Error: {ve}")



processors = dict()

read_and_process_amd(processors, "AMD/amd-all-specification.csv")
read_and_process_ampere_altra(processors, "AMPERE/ampere-altra-specification.csv")
read_and_process_ampere_one(processors, "AMPERE/ampere-one-specification.csv")
read_and_process_intel(processors, "Intel")
read_and_process_green_algorithms(processors, "GreenAlgorithms/TDP_cpu.v2.2.updated.csv")

with open("../TDP_cpu.v2.2.csv", "w") as file:
    file.write("index,in Watt,,,\n")
    file.write("model,TDP,n_cores,TDP_per_core,source")
    for processor in processors.values(): 
        file.write(f"\n{processor['model']},{processor['tdp']},{processor['n_cores']},{processor['tdp_per_core']},{processor['source']}")