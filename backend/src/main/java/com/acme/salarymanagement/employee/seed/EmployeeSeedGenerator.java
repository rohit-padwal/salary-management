package com.acme.salarymanagement.employee.seed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.acme.salarymanagement.employee.model.EmployeeDetails;

final class EmployeeSeedGenerator {

    static final int EMPLOYEE_COUNT = 10_000;
    private static final long RANDOM_SEED = 20261018L;
    private static final List<String> DEPARTMENTS = List.of("Engineering", "Finance", "HR", "Sales",
            "Marketing", "Operations", "Customer Support", "Legal");
    // Illustrative annual salary bands for synthetic demo data, not market compensation advice.
    private static final List<Market> MARKETS = List.of(
            new Market("IN", "INR", 400_000, 5_000_000,
                    names("Asha, Aarav, Priya, Rohit, Kavya, Vikram, Meera, Arjun, Ananya, Nikhil, Divya, Rohan, Sneha, Aditya, Ishita, Rahul"),
                    names("Rao, Sharma, Patel, Nair, Singh, Iyer, Shah, Das, Kumar, Joshi, Mehta, Reddy, Kapoor, Verma, Gupta, Menon")),
            new Market("US", "USD", 35_000, 180_000,
                    names("Grace, James, Olivia, Noah, Ava, Liam, Sophia, Ethan, Amelia, Lucas, Maya, Elijah, Charlotte, Daniel, Isabella, Henry"),
                    names("Johnson, Smith, Williams, Brown, Garcia, Miller, Davis, Wilson, Anderson, Taylor, Thomas, Moore, Martin, Lee, Harris, Clark")),
            new Market("GB", "GBP", 28_000, 130_000,
                    names("Oliver, Amelia, George, Isla, Arthur, Olivia, Harry, Emily, Jack, Sophie, Leo, Freya, Oscar, Alice, Archie, Lily"),
                    names("Smith, Jones, Taylor, Brown, Wilson, Evans, Thomas, Roberts, Johnson, Walker, Wright, Robinson, Thompson, White, Hughes, Edwards")),
            new Market("DE", "EUR", 35_000, 160_000,
                    names("Anna, Lukas, Emma, Leon, Mia, Felix, Hannah, Paul, Lena, Jonas, Marie, Finn, Clara, Elias, Laura, Noah"),
                    names("Müller, Schmidt, Schneider, Fischer, Weber, Meyer, Wagner, Becker, Schulz, Hoffmann, Schäfer, Koch, Bauer, Richter, Klein, Wolf")),
            new Market("JP", "JPY", 3_000_000, 18_000_000,
                    names("佐藤, 鈴木, 高橋, 田中, 伊藤, 渡辺, 山本, 中村, 小林, 加藤, 吉田, 山田, 佐々木, 山口, 松本, 井上"),
                    names("陽菜, 蓮, 結衣, 大翔, 葵, 悠真, 美咲, 湊, 凛, 樹, さくら, 翔太, 愛, 健太, 花, 直樹")),
            new Market("KW", "KWD", 8_000, 35_000,
                    names("أحمد, فاطمة, محمد, مريم, يوسف, نورة, علي, سارة, خالد, دانة, عمر, حصة, حسن, لطيفة, عبدالله, ريم"),
                    names("العنزي, المطيري, العجمي, الرشيدي, الشمري, العتيبي, الهاجري, الدوسري, القحطاني, السالم, الصباح, الحربي, الكندري, العوضي, البلوشي, الدخيل")));

    private EmployeeSeedGenerator() {
    }

    static List<EmployeeDetails> generate(Map<String, Long> departments, Map<String, Integer> precision) {
        for (String name : DEPARTMENTS) {
            Long id = departments.get(name);
            if (id == null || id <= 0) {
                throw new IllegalStateException("Seed requires the configured department: " + name);
            }
        }
        for (Market market : MARKETS) {
            Integer units = precision.get(market.currency());
            if (units == null || units < 0 || units > 4) {
                throw new IllegalStateException("Seed requires supported precision for currency: " + market.currency());
            }
        }
        Random random = new Random(RANDOM_SEED);
        List<EmployeeDetails> employees = new ArrayList<>(EMPLOYEE_COUNT);
        for (int index = 0; index < EMPLOYEE_COUNT; index++) {
            Market market = MARKETS.get(index % MARKETS.size());
            String name = market.firstNames()[random.nextInt(market.firstNames().length)] + " "
                    + market.lastNames()[random.nextInt(market.lastNames().length)];
            Long department = departments.get(DEPARTMENTS.get(random.nextInt(DEPARTMENTS.size())));
            int units = precision.get(market.currency());
            long factor = BigDecimal.TEN.pow(units).longValueExact();
            long amount = random.nextLong(market.minimumSalary() * factor, market.maximumSalary() * factor + 1);
            String salary = BigDecimal.valueOf(amount, units).toPlainString();
            employees.add(new EmployeeDetails(String.format(Locale.ROOT, "SYN-%06d", index + 1), name,
                    department, market.country(), salary, market.currency()));
        }
        return List.copyOf(employees);
    }

    private static String[] names(String values) {
        return values.split(", ");
    }

    private record Market(String country, String currency, long minimumSalary, long maximumSalary,
            String[] firstNames, String[] lastNames) {
    }
}
