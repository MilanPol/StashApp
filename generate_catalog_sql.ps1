$inputTsv = "app/src/main/assets/dutch_catalog.tsv"
$outputSql = "app/src/main/assets/dutch_catalog.sql"

$batchSize = 500
$transactionChunk = 5000 # COMMIT every 5k items to prevent lockouts
$count = 0
$itemsInTransaction = 0
$batch = @()

$quantityRegex = "([\d.,]+)\s*([a-zA-Z]+)"
$digitRegex = "(\d+)"

Write-Host "Turbo SQL: Generating Chunked Transaction Script..."

function Parse-Quantity($raw) {
    if ([string]::IsNullOrWhiteSpace($raw) -or $raw.ToLower() -eq "unknown") { return @($null, $null) }
    
    if ($raw -match $quantityRegex) {
        $amount = $Matches[1].Replace(",", ".")
        $unitStr = $Matches[2].ToLower()
        
        $unit = switch ($unitStr) {
            "g" { "GRAMS" }
            "kg" { "KILOGRAMS" }
            "mg" { "MILLIGRAMS" }
            "l" { "LITERS" }
            "ml" { "MILLILITERS" }
            "cl" { "CENTILITERS" }
            "oz" { "OUNCES" }
            default { "PIECES" }
        }
        return @($amount, $unit)
    } elseif ($raw -like "*pack*") {
        if ($raw -match $digitRegex) {
            $amount = $Matches[1]
        } else {
            $amount = "1"
        }
        return @($amount, "PIECES")
    }
    return @($null, $null)
}

$streamReader = [System.IO.StreamReader]::new($inputTsv)
$streamWriter = [System.IO.StreamWriter]::new($outputSql)

$streamWriter.WriteLine("BEGIN TRANSACTION;")

# Skip Header
$null = $streamReader.ReadLine()

while ($line = $streamReader.ReadLine()) {
    $parts = $line.Split("`t")
    if ($parts.Count -ge 2) {
        $ean = $parts[0].Trim()
        $name = $parts[1].Trim().Replace("'", "''")
        $rawQty = if ($parts.Count -ge 3) { $parts[2].Trim() } else { "" }
        $brand = if ($parts.Count -ge 4) { $parts[3].Trim().Replace("'", "''") } else { "" }

        if ($ean.Length -ge 8 -and $name) {
            $qtyResult = Parse-Quantity $rawQty
            $amount = if ($qtyResult[0]) { "'$($qtyResult[0])'" } else { "NULL" }
            $unit = if ($qtyResult[1]) { "'$($qtyResult[1])'" } else { "NULL" }
            $brandVal = if ($brand) { "'$($brand)'" } else { "NULL" }

            $batch += "('$ean', '$name', $brandVal, $amount, $unit)"
            $count++
            $itemsInTransaction++

            if ($batch.Count -eq $batchSize) {
                $sql = "INSERT OR REPLACE INTO catalog_product (ean, name, brand, default_quantity_amount, default_quantity_unit) VALUES " + ($batch -join ",") + ";"
                $streamWriter.WriteLine($sql)
                $batch = @()
                
                # CHUNK: Periodically COMMIT to release locks
                if ($itemsInTransaction -ge $transactionChunk) {
                    $streamWriter.WriteLine("COMMIT; BEGIN TRANSACTION;")
                    $itemsInTransaction = 0
                    if ($count % 5000 -eq 0) { Write-Host "Committed $count items..." }
                }
            }
        }
    }
}

if ($batch.Count -gt 0) {
    $sql = "INSERT OR REPLACE INTO catalog_product (ean, name, brand, default_quantity_amount, default_quantity_unit) VALUES " + ($batch -join ",") + ";"
    $streamWriter.WriteLine($sql)
}

$streamWriter.WriteLine("COMMIT;")
$streamWriter.Close()
$streamReader.Close()

Write-Host "Turbo SQL Complete: Generated $outputSql with chunked transactions ($count items)"
