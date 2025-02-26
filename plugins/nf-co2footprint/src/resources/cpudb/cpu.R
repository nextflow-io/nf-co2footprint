# Necessary libraries: dplyr
library(dplyr)

# Set working directory
setwd("plugins/nf-co2footprint/src/resources")

# Download data
download.file("http://cpudb.stanford.edu/cpudb.1416196069.zip", "cpudb.zip")
unzip("cpudb.zip", exdir = "cpudb")

# Load data
processor <- read.csv("./cpudb/processor.csv", na.strings = c("NA", "", " ")) %>%
    select(id, model, processor_family_id, manufacturer_id, date, hw_ncores, tdp)
processor_family <- read.csv("./cpudb/processor_familie.csv") %>%
    mutate(family = name) %>%
    select(id, family)
manufacturer <- read.csv("./cpudb/manufacturer.csv") %>%
    mutate(manufacturer = name) %>%
    select(id, manufacturer)

old_tdp <- read.csv("./TDP_cpu.v2.2.csv", na.strings = c("NA", "", " "), header = FALSE) %>%
    rename(
        model = V1, TDP = V2, n_cores = V3, TDP_per_core = V4, source = V5
    )

# Merge all tables
all <- merge(
    processor, processor_family,
    by.x="processor_family_id", by.y="id", all.x=TRUE,
    suffixes=c(".proc", ".proc_family")
)
all <- merge(all, manufacturer,
    by.x="manufacturer_id", by.y="id", all.x=TRUE,
    suffixes=c(".proc", ".proc_manufacturer"), all=TRUE)

all <- all %>%
    filter(
        !is.na(tdp) & !is.na(hw_ncores) & !is.na(model) &
            model != ""
    ) %>%
    rename(
        TDP = tdp, n_cores = hw_ncores
    ) %>%
    mutate(
        TDP_per_core = round(TDP / n_cores, 2),
        source = "http://cpudb.stanford.edu",
        model = trimws(paste(family, model, sep=" ")),
    ) %>%
    select(
        model,TDP,n_cores,TDP_per_core,source
    ) %>%
    filter(
        !duplicated(model) & !model %in% old_tdp$model
    )

# Merge old and new data
new_tdp <- rbind(
    old_tdp,
    all
)

# Write new data
write.table(
    new_tdp, "./TDP_cpu.v2.3.csv", row.names=FALSE, col.names=FALSE, quote=FALSE,
    sep=",", dec=".", na=""
)
