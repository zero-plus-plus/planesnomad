# Planesnomad: download the pictures of only the cards in a CSV file (not a whole picture library).
#
# Usage, from the repository folder after building (mvn compile):
#   .\scripts\download-card-images.ps1 cards.csv
#   .\scripts\download-card-images.ps1 cards.csv more-cards.csv -NoArt      # full pictures only
#   .\scripts\download-card-images.ps1 cards.csv -Check                     # only check the list
#
# CSV columns: name,set,collector_number (header optional; set codes as on Scryfall, e.g. "ody";
# set and number may be empty for the default printing; names with commas in "quotes").
# Pictures go to the game's cache (%LOCALAPPDATA%\Planesnomad\cache\scryfall), where the game
# finds them; already downloaded pictures are skipped, so running it again is quick.
param(
    [Parameter(Mandatory = $true, Position = 0, ValueFromRemainingArguments = $true)] [string[]] $Csv,
    [switch] $NoArt,
    [switch] $Check
)
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
$classpath = (Get-Content ".\classpath.txt" -Raw).Trim()
$toolArgs = @($Csv)
if ($NoArt) { $toolArgs += "--no-art" }
if ($Check) { $toolArgs += "--check" }
java -cp "target\classes;$classpath" com.planesnomad.tools.CardImageDownloader @toolArgs
exit $LASTEXITCODE
