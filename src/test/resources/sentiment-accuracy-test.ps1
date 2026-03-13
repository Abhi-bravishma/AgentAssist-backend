# Sentiment Analysis Accuracy Test Script
# Run: powershell -ExecutionPolicy Bypass -File sentiment-accuracy-test.ps1

$API_BASE_URL = "https://infobip.lab.bravishma.com/agentassist/api/v1"
$DATASET_FILE = "sentiment-test-dataset.json"

# Load test dataset
$dataset = Get-Content $DATASET_FILE | ConvertFrom-Json

# Results tracking
$results = @()
$totalTests = $dataset.testCases.Count
$correctLabel = 0
$correctScore = 0
$truePositive = 0
$trueNegative = 0
$trueNeutral = 0
$falsePositive = 0
$falseNegative = 0
$falseNeutral = 0

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  SENTIMENT ANALYSIS ACCURACY TEST" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total test cases: $totalTests`n"

foreach ($test in $dataset.testCases) {
    $interactionId = "accuracy-test-$($test.id)-$(Get-Random)"

    $body = @{
        interactionId = $interactionId
        from = "user"
        message = $test.message
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$API_BASE_URL/agent-assistant/process" `
            -Method POST `
            -ContentType "application/json" `
            -Body $body `
            -TimeoutSec 60

        $score = $response.currentSentiment

        # Determine predicted label based on score
        if ($score -gt 0.3) {
            $predictedLabel = "positive"
        } elseif ($score -lt -0.3) {
            $predictedLabel = "negative"
        } else {
            $predictedLabel = "neutral"
        }

        # Check label accuracy
        $labelCorrect = $predictedLabel -eq $test.expectedLabel
        if ($labelCorrect) { $correctLabel++ }

        # Check score range accuracy
        $scoreCorrect = ($score -ge $test.expectedScoreMin) -and ($score -le $test.expectedScoreMax)
        if ($scoreCorrect) { $correctScore++ }

        # Confusion matrix tracking
        if ($test.expectedLabel -eq "positive") {
            if ($predictedLabel -eq "positive") { $truePositive++ }
            elseif ($predictedLabel -eq "negative") { $falseNegative++ }
            else { $falseNeutral++ }
        } elseif ($test.expectedLabel -eq "negative") {
            if ($predictedLabel -eq "negative") { $trueNegative++ }
            elseif ($predictedLabel -eq "positive") { $falsePositive++ }
            else { $falseNeutral++ }
        } else {
            if ($predictedLabel -eq "neutral") { $trueNeutral++ }
            elseif ($predictedLabel -eq "positive") { $falsePositive++ }
            else { $falseNegative++ }
        }

        # Display result
        $statusIcon = if ($labelCorrect) { "[PASS]" } else { "[FAIL]" }
        $statusColor = if ($labelCorrect) { "Green" } else { "Red" }

        Write-Host "$statusIcon Test $($test.id): " -ForegroundColor $statusColor -NoNewline
        Write-Host "Expected: $($test.expectedLabel), Got: $predictedLabel (score: $([math]::Round($score, 2)))"

        if (-not $labelCorrect) {
            Write-Host "         Message: `"$($test.message.Substring(0, [Math]::Min(50, $test.message.Length)))...`"" -ForegroundColor Yellow
        }

        $results += @{
            id = $test.id
            message = $test.message
            expectedLabel = $test.expectedLabel
            predictedLabel = $predictedLabel
            score = $score
            labelCorrect = $labelCorrect
            scoreCorrect = $scoreCorrect
            category = $test.category
        }

    } catch {
        Write-Host "[ERROR] Test $($test.id): $($_.Exception.Message)" -ForegroundColor Red
        $results += @{
            id = $test.id
            message = $test.message
            expectedLabel = $test.expectedLabel
            predictedLabel = "error"
            score = 0
            labelCorrect = $false
            scoreCorrect = $false
            category = $test.category
            error = $_.Exception.Message
        }
    }

    # Small delay to avoid rate limiting
    Start-Sleep -Milliseconds 500
}

# Calculate metrics
$labelAccuracy = [math]::Round(($correctLabel / $totalTests) * 100, 2)
$scoreAccuracy = [math]::Round(($correctScore / $totalTests) * 100, 2)

# Precision and Recall for each class
$positivePrecision = if (($truePositive + $falsePositive) -gt 0) {
    [math]::Round($truePositive / ($truePositive + $falsePositive) * 100, 2)
} else { 0 }

$negativePrecision = if (($trueNegative + $falseNegative) -gt 0) {
    [math]::Round($trueNegative / ($trueNegative + $falseNegative) * 100, 2)
} else { 0 }

$positiveRecall = if (($truePositive + $falseNegative + $falseNeutral) -gt 0) {
    $expectedPositives = ($dataset.testCases | Where-Object { $_.expectedLabel -eq "positive" }).Count
    [math]::Round($truePositive / $expectedPositives * 100, 2)
} else { 0 }

$negativeRecall = if (($trueNegative + $falsePositive + $falseNeutral) -gt 0) {
    $expectedNegatives = ($dataset.testCases | Where-Object { $_.expectedLabel -eq "negative" }).Count
    [math]::Round($trueNegative / $expectedNegatives * 100, 2)
} else { 0 }

# F1 Scores
$positiveF1 = if (($positivePrecision + $positiveRecall) -gt 0) {
    [math]::Round(2 * ($positivePrecision * $positiveRecall) / ($positivePrecision + $positiveRecall), 2)
} else { 0 }

$negativeF1 = if (($negativePrecision + $negativeRecall) -gt 0) {
    [math]::Round(2 * ($negativePrecision * $negativeRecall) / ($negativePrecision + $negativeRecall), 2)
} else { 0 }

# Print summary
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "           ACCURACY RESULTS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nOverall Metrics:" -ForegroundColor Yellow
Write-Host "  Label Classification Accuracy: $labelAccuracy% ($correctLabel/$totalTests)"
Write-Host "  Score Range Accuracy: $scoreAccuracy% ($correctScore/$totalTests)"

Write-Host "`nPositive Class:" -ForegroundColor Green
Write-Host "  Precision: $positivePrecision%"
Write-Host "  Recall: $positiveRecall%"
Write-Host "  F1 Score: $positiveF1%"

Write-Host "`nNegative Class:" -ForegroundColor Red
Write-Host "  Precision: $negativePrecision%"
Write-Host "  Recall: $negativeRecall%"
Write-Host "  F1 Score: $negativeF1%"

Write-Host "`nConfusion Summary:" -ForegroundColor Yellow
Write-Host "  True Positives: $truePositive"
Write-Host "  True Negatives: $trueNegative"
Write-Host "  True Neutrals: $trueNeutral"
Write-Host "  False Positives: $falsePositive"
Write-Host "  False Negatives: $falseNegative"

# Category breakdown
Write-Host "`nAccuracy by Category:" -ForegroundColor Yellow
$categories = $results | Group-Object -Property category
foreach ($cat in $categories) {
    $catCorrect = ($cat.Group | Where-Object { $_.labelCorrect }).Count
    $catTotal = $cat.Group.Count
    $catAccuracy = [math]::Round(($catCorrect / $catTotal) * 100, 2)
    Write-Host "  $($cat.Name): $catAccuracy% ($catCorrect/$catTotal)"
}

# Failed tests summary
$failedTests = $results | Where-Object { -not $_.labelCorrect }
if ($failedTests.Count -gt 0) {
    Write-Host "`nFailed Tests:" -ForegroundColor Red
    foreach ($fail in $failedTests) {
        Write-Host "  #$($fail.id): Expected '$($fail.expectedLabel)', Got '$($fail.predictedLabel)' (score: $([math]::Round($fail.score, 2)))"
        Write-Host "      `"$($fail.message)`"" -ForegroundColor Gray
    }
}

Write-Host "`n========================================"
Write-Host "Test completed at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "========================================`n"

# Export results to JSON
$exportData = @{
    timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ss"
    totalTests = $totalTests
    metrics = @{
        labelAccuracy = $labelAccuracy
        scoreAccuracy = $scoreAccuracy
        positivePrecision = $positivePrecision
        positiveRecall = $positiveRecall
        positiveF1 = $positiveF1
        negativePrecision = $negativePrecision
        negativeRecall = $negativeRecall
        negativeF1 = $negativeF1
    }
    results = $results
}

$exportData | ConvertTo-Json -Depth 10 | Out-File "sentiment-test-results.json"
Write-Host "Results exported to sentiment-test-results.json" -ForegroundColor Green
