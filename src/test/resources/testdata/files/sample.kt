package com.opencode.test

/**
 * Sample Kotlin file for testing FileUtils.
 * This file is used to test file path calculation and line selection handling.
 */
class SampleClass {
    
    fun simpleFunction() {
        println("Line 8: Simple function")
    }
    
    fun multiLineFunction() {
        println("Line 12: Start of multi-line function")
        val x = 42
        val y = x * 2
        println("Line 15: End of multi-line function with result: $y")
    }
    
    companion object {
        const val CONSTANT = "Test Constant"
        
        @JvmStatic
        fun staticFunction() {
            println("Static function")
        }
    }
}
