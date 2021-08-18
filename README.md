# Proto Optimisation taille de PDF
mot-clés : Quantization, Palettes, PNG, K-means, K-means++, Silhouette, Otsu Threshold (monochrome), ATSC filter (Grayscale), PDFBox, PNG optimization, Zopfli, Java2D, Apache Commons Math3, RGB, HSV, CieLAB   

Projet expérimental d'optimisation de PDF issus de documents scannés.

Le projet a été commité "tel quel". Il s'agit d'experimentations "brouillons". Il y'a des commentaires dans les classes d'expérimentations.


## Objectif
L'objectif de ce projet est de proposer plusieurs approches pour optimiser la taille des documents scannés en PDF.

Plusieurs approches sont tentées, telles que la quantisation des couleurs (4, 8, 16 bits), Grayscale, Monochrome, l'optimisation de PNG (zopfli)


Plusieurs approches ont été mise en places: certaines se basent sur cette article de blog : https://mzucker.github.io/2016/09/20/noteshrink.html
et notamment une ré-écriture en Java (très instructif d'ailleurs). On se rend compte que Java est bien plus verbeux que ce qui a été fait avec python, num_py et scipy.

Il y'a également une utilisation d'algorithme KMeans, recodée à la main (voir package kmeans), puis finalement en utilisant Apache Commons Math3 (KMeans++) qui est finalement plus simple à utiliser que ce que j'eu cru.


## Conclusion

Il s'avère que toutes les approches n'ont pas été aussi convaincantes que ce qu'on aurait pu penser : 
La taille reste souvent trop conséquente et les images sont trop fortement dégradées.

Optimiser les images avec note_shrink.py est intéressant dans le contexte de prise de notes en tant qu'étudiant (avec un crayon 4 couleurs :-).
Mais pour optimiser des scans de fichiers en provenance d'utilisateurs différents (stabilotés, avec des images, des annotations, etc...), l'approche de quantization n'est pas assez efficace.

De plus, PDFBox limite trop fortement l'optimisation qui aurait pu être possible avec JBIG2 (monochrome) et JPEG2000.
Il n'est pas capable de prendre en compte ces formats.
L'approche retenue (PNG monochrome ou PNG réduit à 4, 8 ou 16 couleurs paletisées) est encore trop couteuse (en taille) par rapport à d'autres approches.

Quelle solution finalement ?
L'approche "PDF Compressée" - proposée par les photocopieurs professionnels, s'appuie sur un algorithme MRC (Mixed Raster Content) et superpose plusieurs couches :
et en tirant partie de JBIG2 et JPEG2000.
Développer soit-même un outil MRC serait bien trop couteux : 
- Nécessité de s'appuyer des algorithmes pour dissocier images et texte (le genre de sujet de thèses qui s'appuient sur des Hidden Markov Model...Bref, bien touchy).
- Nécessité de développer un encodeur JBIG2 et un plugin Writer pour PDFBox (il existe un plugin JBIG2 existant, mais seulement pour la lecture)
- Nécessité de développer un encodeur JPEG2000 et un plugin Writer pour PDFBox (inexistant).

La solution retenue (Foxit PDF Compressor CLT) - anciennement Luratech (qui s'est fait racheté par Foxit) propose un outil clé en main qui propose tout ça et qui, de plus, génère un fichier PDF/A.
Nous partons donc sur cette solution et laissons tomber les expérimentations d'optimisation "maison".

Ce dépot est donc partagé "tel quel", si besoin...







