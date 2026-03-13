#!/bin/bash
# Sentiment Analysis Accuracy Test Script

API_BASE_URL="https://infobip.lab.bravishma.com/agentassist/api/v1"

# Test cases: message|expected_label|min_score|max_score|category
TEST_CASES=(
    "I absolutely love this product! Best purchase ever!|positive|0.6|1.0|strong_positive"
    "Thank you for your help today.|positive|0.3|0.7|mild_positive"
    "This is the worst experience I have ever had!|negative|-1.0|-0.6|strong_negative"
    "I am a bit disappointed with the delivery time.|negative|-0.6|-0.1|mild_negative"
    "Can you tell me my account balance?|neutral|-0.3|0.3|neutral_question"
    "I would like to update my shipping address.|neutral|-0.3|0.3|neutral_request"
    "Your team has been incredibly supportive and professional!|positive|0.6|1.0|strong_positive"
    "I am extremely frustrated with your service!|negative|-1.0|-0.6|strong_negative"
    "The product is okay, nothing special.|neutral|-0.3|0.3|neutral_opinion"
    "I have been waiting for 3 weeks and still no response!|negative|-1.0|-0.4|strong_negative"
    "Amazing customer service! Will recommend to friends.|positive|0.6|1.0|strong_positive"
    "The item arrived damaged and I want a refund.|negative|-0.8|-0.3|negative_complaint"
    "What are your business hours?|neutral|-0.2|0.2|neutral_question"
    "You guys are the best! Thank you so much!|positive|0.7|1.0|strong_positive"
    "This is unacceptable! I demand to speak to a manager!|negative|-1.0|-0.7|strong_negative"
    "I received my order today.|neutral|-0.3|0.3|neutral_statement"
    "Pretty good service overall, thanks.|positive|0.2|0.6|mild_positive"
    "Not happy with the quality of this product.|negative|-0.7|-0.2|mild_negative"
    "Could you please send me the invoice?|neutral|-0.2|0.2|neutral_request"
    "I am so happy with my purchase! Exceeded expectations!|positive|0.7|1.0|strong_positive"
    "Your website is confusing and hard to navigate.|negative|-0.7|-0.2|negative_feedback"
    "I need help with my password reset.|neutral|-0.2|0.2|neutral_request"
    "Wonderful experience from start to finish!|positive|0.7|1.0|strong_positive"
    "This product broke after one day of use!|negative|-0.9|-0.4|strong_negative"
    "My order number is 12345.|neutral|-0.2|0.2|neutral_info"
    "Thanks for the quick response!|positive|0.3|0.7|mild_positive"
    "I am never buying from you again!|negative|-1.0|-0.6|strong_negative"
    "The package was delivered to the wrong address.|negative|-0.6|-0.1|negative_issue"
    "Excellent quality and fast shipping!|positive|0.6|1.0|strong_positive"
    "It works fine I guess.|neutral|-0.2|0.3|neutral_lukewarm"
    "I appreciate your patience with me.|positive|0.3|0.7|mild_positive"
    "Why is this taking so long?!|negative|-0.8|-0.3|negative_impatient"
    "Can I get a status update on my request?|neutral|-0.3|0.2|neutral_question"
    "You have been so helpful, I really appreciate it!|positive|0.6|1.0|strong_positive"
    "This is ridiculous! Fix this immediately!|negative|-1.0|-0.7|strong_negative"
    "I would like to cancel my subscription.|neutral|-0.4|0.1|neutral_request"
    "Great job! Keep up the good work!|positive|0.6|1.0|strong_positive"
    "The support agent was rude and unhelpful.|negative|-0.9|-0.5|strong_negative"
    "Please confirm my appointment for tomorrow.|neutral|-0.2|0.2|neutral_request"
    "I love how easy your app is to use!|positive|0.6|1.0|strong_positive"
    "Still waiting for my refund after a month!|negative|-0.9|-0.5|strong_negative"
    "Do you offer international shipping?|neutral|-0.2|0.2|neutral_question"
    "Pleasantly surprised by the quality!|positive|0.5|0.9|positive_surprise"
    "Complete waste of money!|negative|-1.0|-0.7|strong_negative"
    "I am attaching the required documents.|neutral|-0.2|0.2|neutral_info"
    "Finally got it working! Thanks for your help!|positive|0.5|0.9|positive_relief"
    "Your prices are way too high!|negative|-0.7|-0.3|negative_complaint"
    "How do I track my shipment?|neutral|-0.2|0.2|neutral_question"
    "Five stars! Highly recommended!|positive|0.7|1.0|strong_positive"
    "I feel ignored and not valued as a customer.|negative|-0.8|-0.4|negative_emotional"
)

echo ""
echo "========================================"
echo "  SENTIMENT ANALYSIS ACCURACY TEST"
echo "========================================"
echo "Total test cases: ${#TEST_CASES[@]}"
echo ""

correct_label=0
correct_score=0
total=0
failed_tests=""

for i in "${!TEST_CASES[@]}"; do
    IFS='|' read -r message expected_label min_score max_score category <<< "${TEST_CASES[$i]}"
    test_num=$((i + 1))
    interaction_id="accuracy-test-${test_num}-$$"

    # Call API
    response=$(curl -s -X POST "${API_BASE_URL}/agent-assistant/process" \
        -H "Content-Type: application/json" \
        -d "{\"interactionId\":\"${interaction_id}\",\"from\":\"user\",\"message\":\"${message}\"}" \
        --max-time 60)

    if [ $? -ne 0 ] || [ -z "$response" ]; then
        echo "[ERROR] Test $test_num: API call failed"
        continue
    fi

    # Extract score using grep/sed
    score=$(echo "$response" | grep -o '"currentSentiment":[^,}]*' | sed 's/"currentSentiment"://')

    if [ -z "$score" ]; then
        echo "[ERROR] Test $test_num: Could not parse score"
        continue
    fi

    # Determine predicted label
    if (( $(echo "$score > 0.3" | bc -l) )); then
        predicted_label="positive"
    elif (( $(echo "$score < -0.3" | bc -l) )); then
        predicted_label="negative"
    else
        predicted_label="neutral"
    fi

    total=$((total + 1))

    # Check label accuracy
    if [ "$predicted_label" == "$expected_label" ]; then
        correct_label=$((correct_label + 1))
        echo "[PASS] Test $test_num: Expected=$expected_label, Got=$predicted_label (score=$score)"
    else
        echo "[FAIL] Test $test_num: Expected=$expected_label, Got=$predicted_label (score=$score)"
        echo "       Message: \"${message:0:50}...\""
        failed_tests="${failed_tests}\n  #${test_num}: Expected '${expected_label}', Got '${predicted_label}' - \"${message}\""
    fi

    # Check score range
    if (( $(echo "$score >= $min_score && $score <= $max_score" | bc -l) )); then
        correct_score=$((correct_score + 1))
    fi

    sleep 0.5
done

# Calculate accuracy
label_accuracy=$(echo "scale=2; ($correct_label / $total) * 100" | bc)
score_accuracy=$(echo "scale=2; ($correct_score / $total) * 100" | bc)

echo ""
echo "========================================"
echo "         ACCURACY RESULTS"
echo "========================================"
echo ""
echo "Label Classification Accuracy: ${label_accuracy}% (${correct_label}/${total})"
echo "Score Range Accuracy: ${score_accuracy}% (${correct_score}/${total})"

if [ -n "$failed_tests" ]; then
    echo ""
    echo "Failed Tests:"
    echo -e "$failed_tests"
fi

echo ""
echo "========================================"
echo "Test completed at $(date)"
echo "========================================"
