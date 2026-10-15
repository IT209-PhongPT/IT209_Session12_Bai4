package com.example.demo;

import com.example.demo.service.CalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Kiểm thử đơn vị (Unit Test) cho CalculatorService")
class CalculatorServiceTest {

    private CalculatorService calculatorService;

    @BeforeEach
    void setUp() {
        calculatorService = new CalculatorService();
    }

    @Test
    @DisplayName("Kiểm tra phép cộng hai số nguyên: 15 + 25 = 40")
    void testAdd() {
        int result = calculatorService.add(15, 25);
        assertEquals(40, result, "Kết quả phép tính 15 + 25 phải bằng 40");
    }

    @Test
    @DisplayName("Kiểm tra phép nhân hai số nguyên: 6 * 7 = 42")
    void testMultiply() {
        int result = calculatorService.multiply(6, 7);
        assertEquals(42, result, "Kết quả phép tính 6 * 7 phải bằng 42");
    }

    @Test
    @DisplayName("Kiểm tra số chẵn")
    void testIsEven() {
        assertTrue(calculatorService.isEven(100), "100 phải là số chẵn");
    }
}
