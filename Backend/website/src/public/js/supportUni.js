document.addEventListener("DOMContentLoaded", function () {
    fetchUniversities();
});

function fetchUniversities() {
    fetch("https://localhost:443/universita")
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
                <h5>${uni.nome}</h5>
                <button class="macchinetteButton" data-id="${uni.id}">Apri Macchinette</button>
            </div>
        `;

        albumCol.appendChild(uniElement);
        supportContainer.appendChild(albumCol);
    });

    const addUniButton = document.createElement('div');
    addUniButton.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');
    addUniButton.innerHTML = `
        <div class="university-item">
            <div class="album-info-long">
                <button class="aggiungiCancButton" onclick="showAddUniversitaForm('id')">Aggiungi Università</button>
            </div>
        </div>
    `;
    supportContainer.appendChild(addUniButton);
}

function showAddUniversitaForm(idUni) {
    const supportContainer = document.getElementById("supportContainer");

    const albumCol = document.createElement('div');
    albumCol.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');

    const uniElement = document.createElement("div");
    uniElement.classList.add("university-item");
    
    const formHtml = `
        <div class="album-info-long">
            <h5><label for="universitaNome">Nome dell'università:</label></h5>
            <input type="text" id="universitaNome" placeholder="Inserisci nome università">
            <button class="aggiungiCancButton" onclick="addUniversita('${idUni}')">Aggiungi</button>
            <button class="aggiungiCancButton" onclick="cancelAddUniversita()">Annulla</button>
        </div>
    `;
    albumCol.appendChild(uniElement);
    supportContainer.appendChild(albumCol);
    supportContainer.innerHTML = formHtml;
}

function cancelAddUniversita() {
    fetchUniversities();
}

function addUniversita(idUni) {
    const universitaNome = document.getElementById("universitaNome").value;

    if (universitaNome === "") {
        alert("Il nome della universita è obbligatorio!");
        return;
    }

    const newUniversita = {
        nome: universitaNome,
        id: idUni
    };

    fetch(`https://localhost:443/universita`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(newUniversita)
    })
    .then(response => {
        if (response.ok) {
            alert("Università aggiunta con successo!");
            fetchUniversities();
        } else {
            alert("Errore nell'aggiungere l'università.");
        }
    })
    .catch(error => {
        console.error("Errore durante l'aggiunta dell'università:", error);
        alert("Errore durante l'aggiunta dell'università.");
    });
}

document.addEventListener("click", function (event) {
    if (event.target.classList.contains("macchinetteButton")) {
        event.preventDefault();
        const idUni = event.target.getAttribute("data-id");
        window.location.href = `supportMacchinette?id_uni=${idUni}`;
    }
});