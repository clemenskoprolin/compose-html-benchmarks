import React from "react";

export function FormApp({ data }) {

  return (
    <div id="app-container" role="main">
      <h1>{data.title}</h1>
      <form action={data.actionUrl} method="post" aria-label={data.formLabel}>
        {data.sections.map((section) => (
          <fieldset key={section.id} aria-labelledby={`legend-${section.id}`}>
            <legend id={`legend-${section.id}`}>{section.title}</legend>
            {section.fields.map((field) => {
              const hasError = field.errorMessage != null;
              const ariaDescribedBy = [
                field.helpText ? `help-${field.id}` : null,
                hasError ? `error-${field.id}` : null,
              ]
                .filter(Boolean)
                .join(" ");

              return (
                <div key={field.id} className="form-group" id={`group-${field.id}`}>
                  <label htmlFor={field.id}>{field.label}</label>

                  {/* Field input variants */}
                  {["TEXT", "EMAIL", "PASSWORD", "NUMBER", "TEL"].includes(field.type) && (
                    <input
                      type={field.type.toLowerCase()}
                      id={field.id}
                      name={field.name}
                      defaultValue={field.value || undefined}
                      placeholder={field.placeholder || undefined}
                      required={field.required}
                      disabled={field.disabled}
                      readOnly={field.readOnly}
                      autoComplete={field.autoComplete || undefined}
                      min={field.min || undefined}
                      max={field.max || undefined}
                      minLength={field.minLength || undefined}
                      maxLength={field.maxLength || undefined}
                      pattern={field.pattern || undefined}
                      step={field.step || undefined}
                      aria-required={field.required ? "true" : undefined}
                      aria-invalid={hasError ? "true" : undefined}
                      aria-describedby={ariaDescribedBy || undefined}
                    />
                  )}

                  {field.type === "SELECT" && (
                    <select
                      id={field.id}
                      name={field.name}
                      required={field.required}
                      disabled={field.disabled}
                      multiple={field.multiple}
                      defaultValue={
                        field.options?.find((o) => o.selected)?.value || undefined
                      }
                      aria-required={field.required ? "true" : undefined}
                      aria-invalid={hasError ? "true" : undefined}
                      aria-describedby={ariaDescribedBy || undefined}
                    >
                      {field.options?.map((opt) => (
                        <option key={opt.value} value={opt.value} disabled={opt.disabled}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  )}

                  {field.type === "TEXTAREA" && (
                    <textarea
                      id={field.id}
                      name={field.name}
                      defaultValue={field.value || undefined}
                      placeholder={field.placeholder || undefined}
                      required={field.required}
                      disabled={field.disabled}
                      readOnly={field.readOnly}
                      minLength={field.minLength || undefined}
                      maxLength={field.maxLength || undefined}
                      aria-required={field.required ? "true" : undefined}
                      aria-invalid={hasError ? "true" : undefined}
                      aria-describedby={ariaDescribedBy || undefined}
                    />
                  )}

                  {field.type === "RADIO" && (
                    <div
                      role="radiogroup"
                      aria-required={field.required ? "true" : undefined}
                      aria-invalid={hasError ? "true" : undefined}
                      aria-describedby={ariaDescribedBy || undefined}
                    >
                      {field.options?.map((opt, oIdx) => {
                        const optId = `${field.id}-opt-${oIdx}`;
                        return (
                          <div key={opt.value}>
                            <input
                              type="radio"
                              id={optId}
                              name={field.name}
                              value={opt.value}
                              defaultChecked={opt.selected}
                              disabled={opt.disabled}
                            />
                            <label htmlFor={optId}>{opt.label}</label>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  {field.type === "CHECKBOX" && (
                    <input
                      type="checkbox"
                      id={field.id}
                      name={field.name}
                      defaultValue={field.value || undefined}
                      defaultChecked={field.checked}
                      required={field.required}
                      disabled={field.disabled}
                      aria-required={field.required ? "true" : undefined}
                      aria-invalid={hasError ? "true" : undefined}
                      aria-describedby={ariaDescribedBy || undefined}
                    />
                  )}

                  {field.helpText && (
                    <span id={`help-${field.id}`} className="help-text">
                      {field.helpText}
                    </span>
                  )}

                  {hasError && (
                    <div id={`error-${field.id}`} className="error-message" aria-live="polite">
                      {field.errorMessage}
                    </div>
                  )}
                </div>
              );
            })}
          </fieldset>
        ))}
        <button type="submit" disabled={data.isSubmitting}>
          Submit Registration
        </button>
      </form>
    </div>
  );
}
