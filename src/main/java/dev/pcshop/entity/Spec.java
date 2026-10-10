package dev.pcshop.entity;

import dev.pcshop.enums.SpecType;

/**
 * Характеристика товаров категории: поле в форме товара в админке и строка в карточке на сайте.
 *
 * @param key    ключ в product.attributes, не меняется после создания: по нему товары хранят значения
 * @param filter показывать в фильтре каталога
 */
public record Spec(String key, String label, SpecType type, boolean filter) {
}
