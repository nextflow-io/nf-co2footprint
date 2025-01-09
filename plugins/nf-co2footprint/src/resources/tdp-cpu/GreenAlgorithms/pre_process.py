import pandas as pd

# Load the dataset
file_path = "TDP_cpu.v2.2.csv"  # Replace with your dataset file path
df = pd.read_csv(file_path, skiprows=1)
df = df[df['model'] != 'Any']


# Add 'manufacturer' row based on the model name
df['manufacturer'] = df['model'].apply(lambda x: 'Intel' if 'Xeon' in x or 'Core' in x else 'AMD')

amd_cpus_threads = {
    "A8-7680": 4,
    "A9-9425 SoC": 2,
    "7552": 96,
    "EPYC 7251": 16,
    "Athlon 3000G": 4,
    "FX-6300": 6,
    "FX-8350": 8,
    "Ryzen 3 2200G": 4,
    "Ryzen 3 3200G": 4,
    "Ryzen 3 3200U": 4,
    "Ryzen 5 1600": 12,
    "Ryzen 5 2600": 12,
    "Ryzen 5 3400G": 8,
    "Ryzen 5 3500U": 8,
    "Ryzen 5 3600": 12,
    "Ryzen 5 3600X": 12,
    "Ryzen 7 2700X": 16,
    "Ryzen 7 3700X": 16,
    "Ryzen 7 3800X": 16,
    "Ryzen 9 3900X": 24,
    "Ryzen 9 3950X": 32,
    "Ryzen Threadripper 2990WX": 64,
    "Ryzen Threadripper 3990X": 128,
}

intel_cpus_threads = {
    "Core 2 Quad Q6600": 4,
    "Core i3-10100": 8,
    "Core i3-10300": 8,
    "Core i3-10320": 8,
    "Core i3-10350K": 8,
    "Core i3-9100": 4,
    "Core i3-9100F": 4,
    "Core i5-10400": 12,
    "Core i5-10400F": 12,
    "Core i5-10500": 12,
    "Core i5-10600": 12,
    "Core i5-10600K": 12,
    "Core i5-3570K": 4,
    "Core i5-4460": 4,
    "Core i5-9400": 6,
    "Core i5-9400F": 6,
    "Core i5-9600KF": 6,
    "Core i7-10700": 16,
    "Core i7-10700K": 16,
    "Core i7-4930K": 12,
    "Core i7-6700K": 8,
    "Core i7-8700K": 12,
    "Core i7-9700F": 8,
    "Core i7-9700K": 8,
    "Core i9-10900K": 20,
    "Core i9-10900KF": 20,
    "Core i9-10900XE": 20,
    "Core i9-10920XE": 24,
    "Core i9-9900K": 16,
    "Xeon E5-2660 v3": 20,
    "Xeon E5-2665": 16,
    "Xeon E5-2670": 16,
    "Xeon E5-2670 v2": 20,
    "Xeon E5-2680 v3": 24,
    "Xeon E5-2683 v4": 32,
    "Xeon E5-2690 v2": 20,
    "Xeon E5-2690 v3": 24,
    "Xeon E5-2695 v4": 36,
    "Xeon E5-2697 v4": 36,
    "Xeon E5-2699 v3": 36,
    "Xeon E5-2699 v4": 44,
    "Xeon E5-4610 v4": 20,
    "Xeon E5-4620": 16,
    "Xeon E5-4650L": 16,
    "Xeon E7-8867 v3": 32,
    "Xeon E7-8880 v4": 44,
    "Xeon Gold 6142": 32,
    "Xeon Gold 6148": 40,
    "Xeon Gold 6248": 40,
    "Xeon Gold 6252": 48,
    "Xeon L5640": 12,
    "Xeon Phi 5110P": 240,
    "Xeon Platinum 9282": 112,
    "Xeon X3430": 4,
    "Xeon X5660": 12
}

all_cpus_threads = amd_cpus_threads | intel_cpus_threads
df['model'] = df['model'].apply(lambda x: x.replace("AMD","").strip())
df['threads'] = df['model'].apply(lambda x: all_cpus_threads[x])

# Save the updated dataset or print it
df.to_csv("TDP_cpu.v2.2.updated.csv", index=False)
