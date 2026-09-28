import { useId } from "react";
import { cn } from "@/lib/cn";

const inputBase =
  "w-full rounded-md bg-surface px-4 text-[15px] text-ink ring-1 ring-ink/25 outline-none transition " +
  "placeholder:text-muted/70 hover:ring-ink/50 focus:ring-2 focus:ring-brand";

interface FieldShellProps {
  label: string;
  hint?: string;
  error?: string;
  counter?: { value: number; max: number };
  children: (props: { id: string; describedBy?: string; invalid: boolean }) => React.ReactNode;
}

function FieldShell({ label, hint, error, counter, children }: FieldShellProps) {
  const id = useId();
  const messageId = `${id}-message`;
  const hasMessage = Boolean(error || hint);
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-3">
        <label htmlFor={id} className="text-sm font-semibold text-ink">
          {label}
        </label>
        {counter && (
          <span className={cn("tabular text-xs", counter.value > counter.max ? "text-danger" : "text-muted")}>
            {counter.value}/{counter.max}
          </span>
        )}
      </div>
      {children({ id, describedBy: hasMessage ? messageId : undefined, invalid: Boolean(error) })}
      {hasMessage && (
        <p id={messageId} className={cn("text-[13px] leading-snug", error ? "text-danger" : "text-muted")}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "id"> {
  label: string;
  hint?: string;
  error?: string;
  maxChars?: number;
}

export function TextField({ label, hint, error, maxChars, className, value, ...props }: TextFieldProps) {
  return (
    <FieldShell
      label={label}
      hint={hint}
      error={error}
      counter={maxChars ? { value: String(value ?? "").length, max: maxChars } : undefined}
    >
      {({ id, describedBy, invalid }) => (
        <input
          id={id}
          value={value}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className={cn(inputBase, "h-12", invalid && "ring-2 ring-danger/70", className)}
          {...props}
        />
      )}
    </FieldShell>
  );
}

interface TextAreaProps extends Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, "id"> {
  label: string;
  hint?: string;
  error?: string;
  maxChars?: number;
}

export function TextArea({ label, hint, error, maxChars, className, value, ...props }: TextAreaProps) {
  return (
    <FieldShell
      label={label}
      hint={hint}
      error={error}
      counter={maxChars ? { value: String(value ?? "").length, max: maxChars } : undefined}
    >
      {({ id, describedBy, invalid }) => (
        <textarea
          id={id}
          value={value}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className={cn(inputBase, "min-h-28 resize-y py-3 leading-relaxed", invalid && "ring-2 ring-danger/70", className)}
          {...props}
        />
      )}
    </FieldShell>
  );
}
