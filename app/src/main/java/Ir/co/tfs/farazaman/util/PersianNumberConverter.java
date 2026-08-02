package Ir.co.tfs.farazaman.util;

public class PersianNumberConverter {

    public static String convertPersianToEnglish(String persianNumber) {
        if (persianNumber == null || persianNumber.isEmpty()) {
            return persianNumber;
        }

        StringBuilder englishNumber = new StringBuilder();
        for (char c : persianNumber.toCharArray()) {
            // Check if the character is a Persian digit (Unicode range U+06F0 to U+06F9)
            if (c >= '\u06F0' && c <= '\u06F9') {
                // Convert Persian digit to its English equivalent
                // The difference between the Unicode values of '0' and '۰' is constant
                englishNumber.append((char) (c - '\u06F0' + '0'));
            } else {
                // Append other characters as they are (e.g., non-digit characters, English digits)
                englishNumber.append(c);
            }
        }
        return englishNumber.toString();
    }
}
