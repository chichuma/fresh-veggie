package com.freshveggie.validator;

import com.freshveggie.annotation.validation.MultiFieldAssociationCheck;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

public class MultiFieldAssociationCheckValidator implements ConstraintValidator<MultiFieldAssociationCheck, Object> {

    private String when;
    private String must;
    private String errorField;

    @Override
    public void initialize(MultiFieldAssociationCheck constraintAnnotation) {
        this.when = constraintAnnotation.when();
        this.must = constraintAnnotation.must();
        this.errorField = constraintAnnotation.errorField();
    }

    /**
     * 多字段关联校验：{@code when} 成立时，{@code must} 也必须成立。
     *
     * <h3>2026-10-01 修正（P2-15）</h3>
     * 原实现在 {@code when} 成立后**无条件**执行
     * {@code buildConstraintViolationWithTemplate(...).addConstraintViolation()}，
     * 然后才 {@code return} 表达式结果。也就是说：
     * <ul>
     *   <li>{@code must} 为 true（校验通过）时，违规已经被注册进 context；</li>
     *   <li>返回值又是 true，告诉框架"通过了"。</li>
     * </ul>
     * 这种"返回 true 但已注册违规"的组合是否真的报错，取决于 Hibernate Validator
     * 对 context 的内部处理顺序 —— 属于**依赖实现细节的未定义行为**。
     *
     * <p>正确写法：先算出结果，**只有不通过时**才注册违规，并显式
     * {@code disableDefaultConstraintViolation()} 避免重复报错。
     */
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {

        if (!StringUtils.hasText(when) || !StringUtils.hasText(must)) {
            // spel表达式为空串时直接不校验
            return true;
        }

        ExpressionParser parser = new SpelExpressionParser(); //  创建spel表达式解析器
        StandardEvaluationContext ctx = new StandardEvaluationContext(value);  // 创建对象上下文

        // SpelEvaluationException 出现这个异常代表传入的spel表达式错误
        Expression expressionWhen = parser.parseExpression(when);
        Boolean whenResult = expressionWhen.getValue(ctx, Boolean.class);

        // when 不成立 → 本规则不适用，放行
        if (!Boolean.TRUE.equals(whenResult)) {
            return true;
        }

        Expression expressionMust = parser.parseExpression(must);
        Boolean mustResult = expressionMust.getValue(ctx, Boolean.class);

        // must 成立 → 校验通过，【不要】注册任何违规
        if (Boolean.TRUE.equals(mustResult)) {
            return true;
        }

        // must 不成立 → 注册违规，并把消息挂到指定字段上，便于前端定位
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(errorField)
                .addConstraintViolation();
        return false;
    }
}
