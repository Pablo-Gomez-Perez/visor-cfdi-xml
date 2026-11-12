#!/usr/bin/env bash
# Instala una instantánea inmutable de XSD SAT (mirror phpCfdi).
# No introduce archivos en MySQL ni modifica comprobantes XML.
set -euo pipefail
REVISION="be6963fcb22425dc6055634e0b8dfd5cee50021d"
DESTINO="${1:-${HOME}/.visor-cfdi/esquemas-sat}"
TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$TEMPORAL"' EXIT
if ! command -v curl >/dev/null || ! command -v tar >/dev/null; then
  echo "Se requieren curl y tar" >&2; exit 1
fi
echo "Instalando conjunto XSD público SAT desde phpCfdi, commit: $REVISION"
curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
  "https://github.com/phpcfdi/resources-sat-xml/archive/${REVISION}.tar.gz" \
  --output "$TEMPORAL/sat-xsd.tar.gz"
tar -xzf "$TEMPORAL/sat-xsd.tar.gz" -C "$TEMPORAL"
ORIGEN="$TEMPORAL/resources-sat-xml-${REVISION}/resources/www.sat.gob.mx"
if [ ! -f "$ORIGEN/sitio_internet/cfd/4/cfdv40.xsd" ] ||
   [ ! -f "$ORIGEN/sitio_internet/cfd/catalogos/catCFDI.xsd" ] ||
   [ ! -f "$ORIGEN/esquemas/retencionpago/2/retencionpagov2.xsd" ]; then
  echo "El paquete XSD no contiene los archivos esperados" >&2; exit 1
fi
mkdir -p "$DESTINO"
cp -R "$ORIGEN" "$DESTINO/"
printf '%s\n' "$REVISION" > "$DESTINO/VERSION_FUENTE.txt"
echo "Esquemas instalados en: $DESTINO"
echo "Validar contra fuentes SAT; phpCfdi es una copia distribuida, no el portal oficial."
