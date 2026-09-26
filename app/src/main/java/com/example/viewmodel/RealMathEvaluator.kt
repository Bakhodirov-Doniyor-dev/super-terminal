package com.example.viewmodel

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Professional-grade Mathematical Expression Evaluator.
 * Implements a robust Lexer and AST (Abstract Syntax Tree) Parser.
 * Features:
 * - Proper operator precedence (BODMAS/PEMDAS)
 * - Implicit multiplication (e.g., "2(3+4)", "2pi", "sin(30)cos(60)")
 * - Right-associative exponentiation (2^3^2 = 512)
 * - Support for unary plus/minus
 * - Postfix operators (factorial '!', percentage '%')
 * - Built-in constants (pi, e)
 * - Extensive mathematical functions (sin, cos, tan, sqrt, cbrt, log, ln, abs, floor, ceil, round, etc.)
 * - Radians/Degrees toggle (default degrees for trig functions for better UX)
 * - Floating point error mitigation via precision rounding
 */
object RealMathEvaluator {

    var useDegrees = true // Default to degrees for trigonometric functions

    fun eval(expression: String, isDegrees: Boolean = true): String {
        this.useDegrees = isDegrees
        return try {
            if (expression.isBlank()) return ""
            
            // Handle some common typo replacements safely
            val cleanedExpr = expression
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-") // typographical minus
            
            val tokens = Lexer(cleanedExpr).tokenize()
            val parser = Parser(tokens)
            val ast = parser.parse()
            val result = ast.evaluate()
            
            formatResult(result)
        } catch (e: Exception) {
            "Math Error: ${e.message}"
        }
    }

    private fun formatResult(result: Double): String {
        if (result.isNaN()) return "NaN"
        if (result.isInfinite()) return if (result > 0) "Infinity" else "-Infinity"

        // Mitigate floating point precision issues (e.g. 0.1 + 0.2 = 0.30000000000000004)
        val bd = try {
            BigDecimal(result).round(MathContext(13, RoundingMode.HALF_UP))
        } catch (e: Exception) {
            return result.toString()
        }
        
        val stripped = bd.stripTrailingZeros()
        
        return if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            stripped.toPlainString()
        }
    }

    // --- Lexer ---
    private enum class TokenType {
        NUMBER, IDENTIFIER, PLUS, MINUS, MULTIPLY, DIVIDE, MODULO, POWER, 
        FACTORIAL, PERCENT, LPAREN, RPAREN, EOF
    }

    private data class Token(val type: TokenType, val value: String, val pos: Int)

    private class Lexer(private val input: String) {
        private var pos = 0
        private val length = input.length

        fun tokenize(): List<Token> {
            val tokens = mutableListOf<Token>()
            while (pos < length) {
                val c = input[pos]
                when {
                    c.isWhitespace() -> pos++
                    c == '+' -> tokens.add(Token(TokenType.PLUS, "+", pos++))
                    c == '-' -> tokens.add(Token(TokenType.MINUS, "-", pos++))
                    c == '*' -> tokens.add(Token(TokenType.MULTIPLY, "*", pos++))
                    c == '/' -> tokens.add(Token(TokenType.DIVIDE, "/", pos++))
                    c == '%' -> tokens.add(Token(TokenType.PERCENT, "%", pos++))
                    c == '^' -> tokens.add(Token(TokenType.POWER, "^", pos++))
                    c == '!' -> tokens.add(Token(TokenType.FACTORIAL, "!", pos++))
                    c == '(' -> tokens.add(Token(TokenType.LPAREN, "(", pos++))
                    c == ')' -> tokens.add(Token(TokenType.RPAREN, ")", pos++))
                    c.isDigit() || c == '.' -> tokens.add(readNumber())
                    c.isLetter() -> tokens.add(readIdentifier())
                    // Attempt to treat standalone 'x' as multiplication if it's surrounded by spaces or digits, but it's risky. 
                    // We will just throw on unknown chars.
                    else -> throw IllegalArgumentException("Noma'lum belgi '$c' ($pos-pozitsiyada)")
                }
            }
            tokens.add(Token(TokenType.EOF, "", pos))
            return insertImplicitMultiplications(tokens)
        }

        private fun readNumber(): Token {
            val start = pos
            var dotCount = 0
            while (pos < length && (input[pos].isDigit() || input[pos] == '.' || input[pos] == 'E' || input[pos] == 'e')) {
                if (input[pos] == '.') {
                    dotCount++
                    if (dotCount > 1) throw IllegalArgumentException("Raqamda bir necha nuqta qatnashishi mumkin emas ($pos-pozitsiya)")
                }
                if ((input[pos] == 'E' || input[pos] == 'e') && pos + 1 < length && (input[pos+1] == '+' || input[pos+1] == '-')) {
                    pos += 2 // Skip E+ or E-
                    continue
                }
                pos++
            }
            return Token(TokenType.NUMBER, input.substring(start, pos), start)
        }

        private fun readIdentifier(): Token {
            val start = pos
            while (pos < length && (input[pos].isLetter() || input[pos].isDigit() || input[pos] == '_')) {
                pos++
            }
            val value = input.substring(start, pos)
            if (value.lowercase() == "mod") {
                return Token(TokenType.MODULO, "mod", start)
            }
            return Token(TokenType.IDENTIFIER, value, start)
        }

        private fun insertImplicitMultiplications(tokens: List<Token>): List<Token> {
            val result = mutableListOf<Token>()
            for (i in tokens.indices) {
                val token = tokens[i]
                
                // Hack to replace standalone "x" with "*" if it acts like multiplication operator
                // e.g. "2 x 2". If it's a known function, keep it.
                if (token.type == TokenType.IDENTIFIER && token.value.lowercase() == "x") {
                    result.add(Token(TokenType.MULTIPLY, "*", token.pos))
                } else {
                    result.add(token)
                }
                
                if (i < tokens.size - 1) {
                    val next = tokens[i + 1]
                    val implicitMult = when {
                        result.last().type == TokenType.NUMBER && next.type == TokenType.IDENTIFIER -> true
                        result.last().type == TokenType.NUMBER && next.type == TokenType.LPAREN -> true
                        result.last().type == TokenType.IDENTIFIER && next.type == TokenType.LPAREN -> {
                            val isFunc = isFunction(result.last().value)
                            !isFunc
                        }
                        result.last().type == TokenType.RPAREN && next.type == TokenType.LPAREN -> true
                        result.last().type == TokenType.RPAREN && next.type == TokenType.IDENTIFIER -> true
                        result.last().type == TokenType.RPAREN && next.type == TokenType.NUMBER -> true
                        result.last().type == TokenType.FACTORIAL && next.type != TokenType.EOF && 
                             next.type != TokenType.RPAREN && next.type != TokenType.PLUS && 
                             next.type != TokenType.MINUS && next.type != TokenType.MULTIPLY && 
                             next.type != TokenType.DIVIDE && next.type != TokenType.MODULO && 
                             next.type != TokenType.POWER -> true
                        else -> false
                    }
                    if (implicitMult) {
                        result.add(Token(TokenType.MULTIPLY, "*", result.last().pos))
                    }
                }
            }
            
            // Distinguish MODULO vs PERCENT for '%'
            val finalResult = mutableListOf<Token>()
            for (i in result.indices) {
                val t = result[i]
                if (t.type == TokenType.PERCENT) {
                    val next = if (i + 1 < result.size) result[i+1] else null
                    val isModulo = next != null && (next.type == TokenType.NUMBER || next.type == TokenType.IDENTIFIER || next.type == TokenType.LPAREN)
                    if (isModulo) {
                        finalResult.add(Token(TokenType.MODULO, "%", t.pos))
                    } else {
                        finalResult.add(Token(TokenType.PERCENT, "%", t.pos))
                    }
                } else {
                    finalResult.add(t)
                }
            }
            return finalResult
        }
        
        private fun isFunction(name: String): Boolean {
            val n = name.lowercase()
            return n in listOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
                "sqrt", "cbrt", "log", "ln", "log10", "abs", "round", "floor", "ceil", "sign", "exp")
        }
    }

    // --- AST Nodes ---
    private abstract class Expr {
        abstract fun evaluate(): Double
    }

    private class NumberExpr(val value: Double) : Expr() {
        override fun evaluate() = value
    }

    private class ConstantExpr(val name: String) : Expr() {
        override fun evaluate(): Double = when (name.lowercase()) {
            "pi" -> Math.PI
            "e" -> Math.E
            else -> throw IllegalArgumentException("Noma'lum o'zgarmas (konstanta): $name")
        }
    }

    private class UnaryExpr(val op: TokenType, val right: Expr) : Expr() {
        override fun evaluate(): Double {
            val r = right.evaluate()
            return when (op) {
                TokenType.PLUS -> r
                TokenType.MINUS -> -r
                else -> throw IllegalArgumentException("Noma'lum unar operator $op")
            }
        }
    }

    private class PostfixExpr(val left: Expr, val op: TokenType) : Expr() {
        override fun evaluate(): Double {
            val l = left.evaluate()
            return when (op) {
                TokenType.FACTORIAL -> factorial(l)
                TokenType.PERCENT -> l / 100.0
                else -> throw IllegalArgumentException("Noma'lum postfiks operator $op")
            }
        }
        
        private fun factorial(n: Double): Double {
            if (n < 0 || n != floor(n)) throw IllegalArgumentException("Faktorial faqat manfiy bo'lmagan butun sonlar uchun hisoblanadi")
            if (n > 170) return Double.POSITIVE_INFINITY 
            var res = 1.0
            for (i in 2..n.toLong()) {
                res *= i
            }
            return res
        }
    }

    private class BinaryExpr(val left: Expr, val op: TokenType, val right: Expr) : Expr() {
        override fun evaluate(): Double {
            val l = left.evaluate()
            val r = right.evaluate()
            return when (op) {
                TokenType.PLUS -> l + r
                TokenType.MINUS -> l - r
                TokenType.MULTIPLY -> l * r
                TokenType.DIVIDE -> {
                    if (r == 0.0) throw ArithmeticException("Nolga bo'lish mumkin emas")
                    l / r
                }
                TokenType.MODULO -> l % r
                TokenType.POWER -> l.pow(r)
                else -> throw IllegalArgumentException("Noma'lum binar operator $op")
            }
        }
    }

    private class FunctionExpr(val name: String, val arg: Expr) : Expr() {
        override fun evaluate(): Double {
            val v = arg.evaluate()
            return when (name.lowercase()) {
                "sin" -> sin(if (useDegrees) Math.toRadians(v) else v)
                "cos" -> cos(if (useDegrees) Math.toRadians(v) else v)
                "tan" -> {
                    val rad = if (useDegrees) Math.toRadians(v) else v
                    if (abs(cos(rad)) < 1e-10) throw ArithmeticException("Tangens bu burchak uchun aniqlanmagan")
                    tan(rad)
                }
                "asin" -> {
                    if (v < -1 || v > 1) throw IllegalArgumentException("Arcsin domeni [-1, 1] oralig'ida bo'lishi kerak")
                    if (useDegrees) Math.toDegrees(asin(v)) else asin(v)
                }
                "acos" -> {
                    if (v < -1 || v > 1) throw IllegalArgumentException("Arccos domeni [-1, 1] oralig'ida bo'lishi kerak")
                    if (useDegrees) Math.toDegrees(acos(v)) else acos(v)
                }
                "atan" -> if (useDegrees) Math.toDegrees(atan(v)) else atan(v)
                "sinh" -> sinh(v)
                "cosh" -> cosh(v)
                "tanh" -> tanh(v)
                "sqrt" -> {
                    if (v < 0) throw IllegalArgumentException("Manfiy sondan kvadrat ildiz olish mumkin emas")
                    sqrt(v)
                }
                "cbrt" -> cbrt(v)
                "log", "log10" -> {
                    if (v <= 0) throw IllegalArgumentException("Logarifm faqat musbat sonlar uchun aniqlangan")
                    log10(v)
                }
                "ln" -> {
                    if (v <= 0) throw IllegalArgumentException("Natural logarifm faqat musbat sonlar uchun aniqlangan")
                    ln(v)
                }
                "exp" -> exp(v)
                "abs" -> abs(v)
                "round" -> round(v)
                "floor" -> floor(v)
                "ceil" -> ceil(v)
                "sign" -> sign(v)
                else -> throw IllegalArgumentException("Noma'lum funksiya: $name")
            }
        }
    }

    // --- Parser (Recursive Descent) ---
    private class Parser(private val tokens: List<Token>) {
        private var pos = 0
        private val current: Token get() = tokens[pos]

        private fun consume(type: TokenType): Token {
            if (current.type == type) {
                return tokens[pos++]
            }
            throw IllegalArgumentException("Kutilgan belgi: $type, lekin ${current.value} topildi (${current.pos}-pozitsiya)")
        }

        private fun match(vararg types: TokenType): Boolean {
            if (current.type in types) {
                pos++
                return true
            }
            return false
        }

        fun parse(): Expr {
            val expr = parseExpression()
            if (current.type != TokenType.EOF) {
                throw IllegalArgumentException("Kutilmagan belgi ${current.value} (${current.pos}-pozitsiyada)")
            }
            return expr
        }

        // Expression -> Term (('+ ' | '-') Term)*
        private fun parseExpression(): Expr {
            var expr = parseTerm()
            while (true) {
                val opToken = current
                if (match(TokenType.PLUS, TokenType.MINUS)) {
                    val right = parseTerm()
                    expr = BinaryExpr(expr, opToken.type, right)
                } else {
                    break
                }
            }
            return expr
        }

        // Term -> Factor (('*' | '/' | 'mod') Factor)*
        private fun parseTerm(): Expr {
            var expr = parseFactor()
            while (true) {
                val opToken = current
                if (match(TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MODULO)) {
                    val right = parseFactor()
                    expr = BinaryExpr(expr, opToken.type, right)
                } else {
                    break
                }
            }
            return expr
        }

        // Factor -> Unary ('^' Unary)*
        private fun parseFactor(): Expr {
            var expr = parseUnary()
            if (current.type == TokenType.POWER) {
                val opToken = tokens[pos++]
                // Power is right-associative, so we recursively call parseFactor()
                val right = parseFactor()
                expr = BinaryExpr(expr, opToken.type, right)
            }
            return expr
        }

        // Unary -> ('+' | '-') Unary | Postfix
        private fun parseUnary(): Expr {
            val opToken = current
            if (match(TokenType.PLUS, TokenType.MINUS)) {
                return UnaryExpr(opToken.type, parseUnary())
            }
            return parsePostfix()
        }

        // Postfix -> Primary ('!' | '%')*
        private fun parsePostfix(): Expr {
            var expr = parsePrimary()
            while (true) {
                val opToken = current
                if (match(TokenType.FACTORIAL, TokenType.PERCENT)) {
                    expr = PostfixExpr(expr, opToken.type)
                } else {
                    break
                }
            }
            return expr
        }

        // Primary -> NUMBER | IDENTIFIER | IDENTIFIER '(' Expression ')' | '(' Expression ')'
        private fun parsePrimary(): Expr {
            val token = current
            when (token.type) {
                TokenType.NUMBER -> {
                    consume(TokenType.NUMBER)
                    try {
                        return NumberExpr(token.value.toDouble())
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Raqam formati noto'g'ri: ${token.value}")
                    }
                }
                TokenType.IDENTIFIER -> {
                    consume(TokenType.IDENTIFIER)
                    if (current.type == TokenType.LPAREN) {
                        consume(TokenType.LPAREN)
                        val arg = parseExpression()
                        consume(TokenType.RPAREN)
                        return FunctionExpr(token.value, arg)
                    } else {
                        return ConstantExpr(token.value)
                    }
                }
                TokenType.LPAREN -> {
                    consume(TokenType.LPAREN)
                    val expr = parseExpression()
                    consume(TokenType.RPAREN)
                    return expr
                }
                else -> throw IllegalArgumentException("Kutilmagan ifoda yoki belgi ${token.value} (${token.pos}-pozitsiyada)")
            }
        }
    }
}
