package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(

        @NotNull(message = "Поставьте оценку")
        @Min(value = 1, message = "Оценка — от 1 до 5")
        @Max(value = 5, message = "Оценка — от 1 до 5")
        Integer rating,

        @Size(max = 1000, message = "Достоинства — не длиннее 1000 символов")
        String pros,

        @Size(max = 1000, message = "Недостатки — не длиннее 1000 символов")
        String cons,

        @NotBlank(message = "Напишите пару слов о товаре")
        @Size(min = 10, max = 2000, message = "Комментарий — от 10 до 2000 символов")
        String text) {

    public ReviewRequest {
        pros = Strings.blankToNull(pros);
        cons = Strings.blankToNull(cons);
        text = Strings.strip(text);
    }
}
