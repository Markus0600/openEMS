#!/bin/bash

# Test validation script for Sensata BMS State Machine tests
# This script validates the test structure and provides a summary

echo "=========================================="
echo "Sensata BMS State Machine Test Validation"
echo "=========================================="

TEST_DIR="/home/runner/work/openEMS/openEMS/io.openems.edge.battery.sensatabms/test/io/openems/edge/battery/sensatabms/statemachine"

echo "Test directory: $TEST_DIR"
echo ""

# Count test files
echo "Test Files:"
echo "----------"
test_files=$(find "$TEST_DIR" -name "*Test.java" | wc -l)
mock_files=$(find "$TEST_DIR" -name "Mock*.java" | wc -l)
total_files=$(find "$TEST_DIR" -name "*.java" | wc -l)

echo "Test classes: $test_files"
echo "Mock classes: $mock_files"
echo "Total Java files: $total_files"
echo ""

# List all test files
echo "Test Classes:"
echo "------------"
for file in $(find "$TEST_DIR" -name "*Test.java" | sort); do
    filename=$(basename "$file")
    test_count=$(grep -c "@Test" "$file")
    echo "  $filename ($test_count tests)"
done
echo ""

# List mock files
echo "Mock Classes:"
echo "------------"
for file in $(find "$TEST_DIR" -name "Mock*.java" | sort); do
    filename=$(basename "$file")
    echo "  $filename"
done
echo ""

# Count total test methods
echo "Test Method Count:"
echo "-----------------"
total_tests=0
for file in $(find "$TEST_DIR" -name "*Test.java"); do
    test_count=$(grep -c "@Test" "$file")
    total_tests=$((total_tests + test_count))
done
echo "Total @Test methods: $total_tests"
echo ""

# Validate test structure
echo "Structure Validation:"
echo "--------------------"

# Check for required state handler tests
required_handlers=("UndefinedHandlerTest" "IdleHandlerTest" "GoRunningHandlerTest" "RunningHandlerTest" "GoStoppedHandlerTest" "DischargeHandlerTest" "ErrorHandlerTest")
missing_handlers=()

for handler in "${required_handlers[@]}"; do
    if [ -f "$TEST_DIR/${handler}.java" ]; then
        echo "✓ $handler.java found"
    else
        echo "✗ $handler.java missing"
        missing_handlers+=("$handler")
    fi
done

# Check for integration tests
if [ -f "$TEST_DIR/StateMachineTest.java" ]; then
    echo "✓ StateMachineTest.java found"
else
    echo "✗ StateMachineTest.java missing"
fi

if [ -f "$TEST_DIR/ContextTest.java" ]; then
    echo "✓ ContextTest.java found"
else
    echo "✗ ContextTest.java missing"
fi

if [ -f "$TEST_DIR/StateMachineFaultInjectionTest.java" ]; then
    echo "✓ StateMachineFaultInjectionTest.java found"
else
    echo "✗ StateMachineFaultInjectionTest.java missing"
fi

echo ""

# Check for mock infrastructure
echo "Mock Infrastructure:"
echo "------------------"
if [ -f "$TEST_DIR/MockSensataBms.java" ]; then
    echo "✓ MockSensataBms.java found"
else
    echo "✗ MockSensataBms.java missing"
fi

if [ -f "$TEST_DIR/MockIntegerWriteChannel.java" ]; then
    echo "✓ MockIntegerWriteChannel.java found"
else
    echo "✗ MockIntegerWriteChannel.java missing"
fi

if [ -f "$TEST_DIR/MockIntegerReadChannel.java" ]; then
    echo "✓ MockIntegerReadChannel.java found"
else
    echo "✗ MockIntegerReadChannel.java missing"
fi

echo ""

# Summary
echo "Summary:"
echo "-------"
if [ ${#missing_handlers[@]} -eq 0 ]; then
    echo "✓ All required state handler tests present"
else
    echo "✗ Missing state handler tests: ${missing_handlers[*]}"
fi

if [ $total_tests -ge 100 ]; then
    echo "✓ Comprehensive test coverage ($total_tests tests)"
else
    echo "⚠ Test coverage could be improved ($total_tests tests)"
fi

echo ""
echo "Test suite validation complete!"
echo ""

# Check Java syntax (basic validation)
echo "Syntax Check:"
echo "------------"
syntax_errors=0
for file in $(find "$TEST_DIR" -name "*.java"); do
    # Basic syntax check - look for unmatched braces
    open_braces=$(grep -o '{' "$file" | wc -l)
    close_braces=$(grep -o '}' "$file" | wc -l)
    
    if [ $open_braces -ne $close_braces ]; then
        echo "⚠ Potential syntax issue in $(basename "$file"): $open_braces { vs $close_braces }"
        syntax_errors=$((syntax_errors + 1))
    fi
done

if [ $syntax_errors -eq 0 ]; then
    echo "✓ No obvious syntax issues detected"
else
    echo "⚠ $syntax_errors files may have syntax issues"
fi

echo ""
echo "Validation complete!"