


# 1 Conversion MUPDF(PNG) -> ImageMagick(JPG) -> PDF/A

La taille ne descend pas suffisamment.

# 2 Conversion MUPDF(PNG) -> JBIG2 
Le JBIG2 est accepté par PDF/A 1


tombe à l'eau.
PDFBox peut (via un plugin) lire du JBIG2. Mais ne peut pas l'écrire.
Il n'existe pas de plugin Java pour Ecrire du JBIG2.


# 3 Conversion MUPDF(PNG) -> ImagaMAgick JP2 -> PDF/A 

le JPEG 2000 est accepté à partir de PDF/A - 2
Le PDF/A 3 est toléré par le RGI.

Sauf que PDFBox ne permet pas d'écrire des images au format JP2 (JPXFilter n'est pas supporté)

bon ca craint...

Repasser à iText ? en version payante ?


## Autre chose ?
ImageMagick est capable de convertir en JBIG2

magick -list compress

https://en.wikipedia.org/wiki/JBIG2#Technical_details


 