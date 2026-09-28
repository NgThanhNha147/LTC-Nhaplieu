import { useQuery } from "@tanstack/react-query";
import { Checkbox, DatePicker, Input, InputNumber, Select, Switch } from "antd";
import dayjs from "dayjs";
import { Controller, type Control, type FieldValues } from "react-hook-form";
import { recordApi } from "../api/records";
import type { FieldDefinition } from "../types";

function LookupInput({ field, value, onChange, disabled }: { field: FieldDefinition; value: unknown; onChange: (value: unknown) => void; disabled?: boolean }) {
  const [query, setQuery] = React.useState("");
  const lookupCode = field.lookup?.lookupCode ?? "";
  const options = useQuery({ queryKey: ["lookup", lookupCode, query], queryFn: () => recordApi.lookup(lookupCode, query), enabled: Boolean(lookupCode) });
  return (
    <Select
      showSearch
      allowClear={field.lookup?.allowClear !== false}
      filterOption={false}
      value={value as string | number | undefined}
      disabled={disabled}
      placeholder={field.placeholder ?? `Chọn ${field.label.toLowerCase()}`}
      options={options.data}
      loading={options.isFetching}
      onSearch={setQuery}
      onChange={onChange}
    />
  );
}

// React import is kept explicit because the lookup component owns debounced query state.
import React from "react";

export function DynamicField({ field, control, disabled }: { field: FieldDefinition; control: Control<FieldValues>; disabled?: boolean }) {
  const rules: Record<string, unknown> = {};
  const validators: Record<string, (input: unknown) => true | string> = {};
  if (field.required) rules.required = `${field.label} là bắt buộc`;
  if (field.dataType === "EMAIL") {
    validators.email = (input) => !input || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(String(input)) || "Email không hợp lệ";
  }
  if (field.maxLength) rules.maxLength = { value: field.maxLength, message: `Tối đa ${field.maxLength} ký tự` };
  for (const rule of field.validations ?? []) {
    const value = rule.ruleConfig?.value;
    if (rule.ruleType === "MIN_LENGTH" && value !== undefined) rules.minLength = { value: Number(value), message: rule.errorMessage };
    if (rule.ruleType === "MAX_LENGTH" && value !== undefined) rules.maxLength = { value: Number(value), message: rule.errorMessage };
    if (rule.ruleType === "MIN_VALUE" && value !== undefined) rules.min = { value: Number(value), message: rule.errorMessage };
    if (rule.ruleType === "MAX_VALUE" && value !== undefined) rules.max = { value: Number(value), message: rule.errorMessage };
    if (rule.ruleType === "REGEX" && (rule.ruleConfig?.pattern || value)) rules.pattern = { value: new RegExp(String(rule.ruleConfig?.pattern ?? value)), message: rule.errorMessage };
    if (rule.ruleType === "DATE_RANGE") {
      validators.dateRange = (input) => {
        if (!input) return true;
        const date = dayjs(String(input));
        const min = rule.ruleConfig?.min ? dayjs(String(rule.ruleConfig.min)) : undefined;
        const max = rule.ruleConfig?.max ? dayjs(String(rule.ruleConfig.max)) : undefined;
        return ((!min || !date.isBefore(min, "day")) && (!max || !date.isAfter(max, "day"))) || rule.errorMessage || "Ngày nằm ngoài khoảng cho phép";
      };
    }
    if (rule.ruleType === "DECIMAL_SCALE" && rule.ruleConfig?.scale !== undefined) {
      validators.decimalScale = (input) => {
        if (input === undefined || input === null || input === "") return true;
        const decimals = String(input).split(".")[1]?.length ?? 0;
        return decimals <= Number(rule.ruleConfig?.scale) || rule.errorMessage || `Tối đa ${rule.ruleConfig?.scale} chữ số thập phân`;
      };
    }
  }
  if (Object.keys(validators).length) rules.validate = validators;

  return (
    <Controller
      name={field.fieldCode}
      control={control}
      defaultValue={field.defaultValue ?? (field.dataType === "BOOLEAN" ? false : undefined)}
      rules={rules}
      render={({ field: binding, fieldState }) => (
        <div className={`dynamic-field ${fieldState.error ? "has-error" : ""}`}>
          <label htmlFor={field.fieldCode}>{field.label}{field.required && <span className="required-mark"> *</span>}</label>
          {field.componentType === "TEXTAREA" || field.dataType === "TEXTAREA" ? (
            <Input.TextArea {...binding} value={(binding.value as string) ?? ""} rows={3} disabled={disabled || field.readOnly} placeholder={field.placeholder} />
          ) : field.componentType === "NUMBER" || ["INTEGER", "DECIMAL"].includes(field.dataType) ? (
            <InputNumber id={field.fieldCode} value={binding.value as number | null} onChange={binding.onChange} onBlur={binding.onBlur} disabled={disabled || field.readOnly} placeholder={field.placeholder} precision={field.dataType === "DECIMAL" ? field.scale : 0} style={{ width: "100%" }} />
          ) : field.componentType === "DATE" || field.componentType === "DATETIME" ? (
            <DatePicker id={field.fieldCode} value={binding.value ? dayjs(binding.value as string) : null} format={field.componentType === "DATETIME" ? "DD-MM-YYYY HH:mm" : "DD-MM-YYYY"} onChange={(date) => binding.onChange(date ? (field.componentType === "DATE" ? date.format("YYYY-MM-DD") : date.toISOString()) : null)} showTime={field.componentType === "DATETIME"} disabled={disabled || field.readOnly} placeholder={field.placeholder ?? (field.componentType === "DATETIME" ? "DD-MM-YYYY HH:mm" : "DD-MM-YYYY")} style={{ width: "100%" }} />
          ) : field.componentType === "CHECKBOX" ? (
            <Checkbox checked={Boolean(binding.value)} onChange={(event) => binding.onChange(event.target.checked)} disabled={disabled || field.readOnly}>Có / Đúng</Checkbox>
          ) : field.componentType === "SWITCH" ? (
            <Switch checked={Boolean(binding.value)} onChange={binding.onChange} disabled={disabled || field.readOnly} />
          ) : ["SELECT", "AUTOCOMPLETE"].includes(field.componentType) || field.dataType === "LIST" ? (
            <LookupInput field={field} value={binding.value} onChange={binding.onChange} disabled={disabled || field.readOnly} />
          ) : (
            <Input {...binding} id={field.fieldCode} value={(binding.value as string) ?? ""} type={field.dataType === "EMAIL" ? "email" : "text"} disabled={disabled || field.readOnly} placeholder={field.placeholder} maxLength={field.maxLength} />
          )}
          {field.helpText && !fieldState.error && <small>{field.helpText}</small>}
          {fieldState.error && <small className="field-error">{fieldState.error.message}</small>}
        </div>
      )}
    />
  );
}
