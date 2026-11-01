# -*- coding: utf-8 -*-
"""One-time: download Argos Translate ru->en model for offline use."""
from argostranslate import package

package.update_package_index()
available = package.get_available_packages()
pair = [p for p in available if p.from_code == "ru" and p.to_code == "en"]
if not pair:
    raise SystemExit("No ru->en package found")
path = pair[0].download()
package.install_from_path(path)
print("Installed ru -> en")
