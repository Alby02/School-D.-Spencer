document.addEventListener("DOMContentLoaded", function () {
    fetchMacchinette();
});

function fetchMacchinette() {
    const urlParams = new URLSearchParams(window.location.search);
    const idUni = urlParams.get("id_uni");
    
    fetch(`http://localhost:8443/macchinette/${idUni}`)
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

function showAddMacchinettaForm(idUni) {
    const supportContainer = document.getElementById("supportContainer");
    
    const formHtml = `
        <div id="addMacchinettaForm">
            <label for="macchinettaNome">Nome della Macchinetta:</label>
            <input type="text" id="macchinettaNome" placeholder="Inserisci nome macchinetta">
            <button onclick="addMacchinetta('${idUni}')">Aggiungi</button>
            <button onclick="cancelAddMacchinetta()">Annulla</button>
        </div>
    `;
    
    supportContainer.innerHTML = formHtml;
}

function cancelAddMacchinetta() {
    fetchMacchinette();
}

function addMacchinetta(idUni) {
    const macchinettaNome = document.getElementById("macchinettaNome").value;

    if (macchinettaNome === "") {
        alert("Il nome della macchinetta è obbligatorio!");
        return;
    }

    const newMacchinetta = {
        nome: macchinettaNome,
        id_uni: idUni
    };

    fetch("http://localhost:8443/macchinette", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(newMacchinetta)
    })
    .then(response => {
        if (response.ok) {
            alert("Macchinetta aggiunta con successo!");
            fetchMacchinette();
        } else {
            alert("Errore nell'aggiungere la macchinetta.");
        }
    })
    .catch(error => {
        console.error("Errore durante l'aggiunta della macchinetta:", error);
        alert("Errore durante l'aggiunta della macchinetta.");
    });
}