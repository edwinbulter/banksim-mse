{{/* Gemeenschappelijke labels; Services selecteren op naam + instance. */}}
{{- define "banksim-mse.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: banksim-mse
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
{{- end }}

{{- define "banksim-mse.selector" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{- define "banksim-mse.image" -}}
{{ index .root.Values.images .key }}:{{ .root.Values.imageTag }}
{{- end }}
