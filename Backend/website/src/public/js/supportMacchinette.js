document.addEventListener("DOMContentLoaded", function () {
    fetchMacchinette();
});

function fetchMacchinette() {
    const urlParams = new URLSearchParams(window.location.search);
    const idUni = urlParams.get("id_uni");
    
    fetch(`http://localhost:8888/macchinette/${idUni}`)
        .then(response => {
            if (!response.ok) {
                throw new Error("Errore nel recupero dei dati");
            }
            response.json().then(displayMacchinette);
        })

        .catch(error => {
            console.error("Errore durante il recupero delle macchinette:", error);
        });
}

function displayMacchinette(macchinette) {
    const supportContainer = document.getElementById("supportContainer");
    supportContainer.innerHTML = "";
    
    macchinette.forEach(macchinetta => {
        const albumCol = document.createElement('div');
        albumCol.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');

        const uniElement = document.createElement("div");
        uniElement.classList.add("macchinetta-item");

        uniElement.innerHTML = `
            <div class="album-info-long">
                <a href="#">
                    <h5>${macchinetta.nome}</h5>
                </a>
            </div>
        `;

        albumCol.appendChild(uniElement);
        supportContainer.appendChild(albumCol);
    });
}