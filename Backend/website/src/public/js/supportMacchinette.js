document.addEventListener("DOMContentLoaded", function () {
    fetchUniversities();
});

function fetchUniversities() {
    fetch("http://tuo-server.com/api/universities") // Modifica con il tuo endpoint
        .then(response => {
            if (!response.ok) {
                throw new Error("Errore nel recupero dei dati");
            }
            return response.json();
        })
        .then(data => {
            displayUniversities(data);
        })
        .catch(error => {
            console.error("Errore durante il recupero delle università:", error);
        });
}

function displayUniversities(universities) {
    const supportContainer = document.getElementById("supportContainer");
    supportContainer.innerHTML = "";
    
    universities.forEach(uni => {
        const albumCol = document.createElement('div');
        albumCol.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');

        const uniElement = document.createElement("div");
        uniElement.classList.add("macchinetta-item");

        uniElement.innerHTML = `
            <div class="album-info">
                <a href="#">
                    <h5>${macchinetta.name}</h5>
                    <h5>Quantità: ${macchinetta.quantity}</h5>
                    <button class="macchinettaButton" data-product-id="${macchinetta.product_id}">Apri Macchinette</button>
                </a>
            </div>
        `;

        albumCol.appendChild(uniElement);
        supportContainer.appendChild(albumCol);
    });
}