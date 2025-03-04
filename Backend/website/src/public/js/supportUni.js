document.addEventListener("DOMContentLoaded", function () {
    fetchUniversities();
});

function fetchUniversities() {
    fetch("http://localhost:8888/universita")
        .then(response => {
            if (!response.ok) {
                throw new Error("Errore nel recupero dei dati");
            }
            response.json().then(displayUniversities);
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
        uniElement.classList.add("university-item");

        uniElement.innerHTML = `
            <div class="album-info-long">
                <a href="#">
                    <h5>${uni.nome}</h5>
                    <button class="macchinetteButton" data-id="${uni.id}">Apri Macchinette</button>
                </a>
            </div>
        `;

        albumCol.appendChild(uniElement);
        supportContainer.appendChild(albumCol);
    });
    
}

document.addEventListener("click", function (event) {
    if (event.target.classList.contains("macchinetteButton")) {
        event.preventDefault();
        const idUni = event.target.getAttribute("data-id");
        window.location.href = `supportMacchinette?id_uni=${idUni}`;
    }
});