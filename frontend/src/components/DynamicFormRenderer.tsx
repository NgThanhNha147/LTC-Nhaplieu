import { Collapse } from "antd";
import type { Control, FieldValues } from "react-hook-form";
import type { FormMetadata } from "../types";
import { DynamicField } from "./DynamicField";

export function DynamicFormRenderer({ metadata, control, disabled }: { metadata: FormMetadata; control: Control<FieldValues>; disabled?: boolean }) {
  const groups = [...metadata.groups].sort((a, b) => a.displayOrder - b.displayOrder);
  return (
    <div className="runtime-form">
      {groups.map((group) => {
        const body = (
          <div className={`runtime-field-grid columns-${group.columnCount ?? 3}`}>
            {[...(group.fields ?? [])].filter((field) => !field.hidden).sort((a, b) => a.displayOrder - b.displayOrder).map((field) => (
              <div key={field.id || field.fieldCode} style={{ gridColumn: `span ${Math.min(12, Math.max(1, field.gridSpan ?? 4))}` }}>
                <DynamicField field={field} control={control} disabled={disabled} />
              </div>
            ))}
          </div>
        );
        return group.collapsible ? (
          <Collapse key={group.id || group.code} defaultActiveKey={group.defaultCollapsed ? [] : [group.id || group.code]} className="runtime-group" items={[{ key: group.id || group.code, label: <div><strong>{group.label}</strong>{group.description && <small>{group.description}</small>}</div>, children: body }]} />
        ) : (
          <section className="runtime-group static" key={group.id || group.code}><div className="runtime-group-heading"><strong>{group.label}</strong>{group.description && <small>{group.description}</small>}</div>{body}</section>
        );
      })}
    </div>
  );
}
